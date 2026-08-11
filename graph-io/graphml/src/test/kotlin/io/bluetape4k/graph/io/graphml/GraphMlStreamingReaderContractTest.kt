package io.bluetape4k.graph.io.graphml

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class GraphMlStreamingReaderContractTest {

    @Test
    fun `reader emits vertices and edges in source order`() = runSuspendIO {
        val xml = graphMl(
            """
            <node id="v1"/>
            <node id="v2"/>
            <edge id="e1" source="v1" target="v2"/>
            """.trimIndent(),
        )

        GraphMlRecordFlowReader().readVertices(sourceOf(xml)).toList()
            .map { it.externalId } shouldBeEqualTo listOf("v1", "v2")
        GraphMlRecordFlowReader().readEdges(sourceOf(xml)).toList()
            .map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `reader preserves source ownership and closes owned input exactly once`() = runSuspendIO {
        val callerOwned = TrackingInputStream(graphMl("<node id=\"v1\"/>").toByteArray())
        val owned = TrackingInputStream(graphMl("<node id=\"v1\"/>").toByteArray())

        GraphMlRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(callerOwned),
        ).toList()
        GraphMlRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(owned, closeInput = true),
        ).toList()

        callerOwned.closed.shouldBeFalse()
        owned.closed.shouldBeTrue()
        owned.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one cancels stax producer before full input collection`() = runSuspendIO {
        val xml = graphMl((1..10_000).joinToString("\n") { "<node id=\"v$it\"/>" })
        val input = TrackingInputStream(xml.toByteArray())

        val first = GraphMlRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(input),
        ).take(1).toList()

        first.single().externalId shouldBeEqualTo "v1"
        (input.bytesRead < xml.toByteArray().size).shouldBeTrue()
    }

    @Test
    fun `malformed xml exposes safe reader failure`() = runSuspendIO {
        val source = GraphImportSource.InputStreamSource(
            graphMl("<node id=\"secret-record\"><data>secret-payload").toByteArray().inputStream(),
        )

        val error = assertFailsWith<GraphIoReadException> {
            GraphMlRecordFlowReader().readVertices(source).toList()
        }

        error.message.orEmpty() shouldNotContain "secret-payload"
        error.message.orEmpty() shouldNotContain "secret-record"
    }

    @Test
    fun `bulk importer enforces bounded edge buffer`() {
        val xml = graphMl(
            """
            <node id="v1"/>
            <node id="v2"/>
            <node id="v3"/>
            <edge id="e1" source="v1" target="v2"/>
            <edge id="e2" source="v2" target="v3"/>
            """.trimIndent(),
        )

        val report = GraphMlBulkImporter().importGraph(
            source = sourceOf(xml),
            operations = TinkerGraphOperations(),
            options = GraphImportOptions(maxEdgeBufferSize = 1),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.edgesRead shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 0L
        report.failures.single().message shouldBeEqualTo
            "Edge buffer exceeded maxEdgeBufferSize=1; verticesCreated=0 remain in graph as partial state"
    }

    private fun sourceOf(xml: String): GraphImportSource =
        GraphImportSource.InputStreamSource(xml.byteInputStream())

    private fun graphMl(body: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
           |<graphml xmlns="http://graphml.graphdrawing.org/graphml">
           |  <graph id="G" edgedefault="directed">
           |    $body
           |  </graph>
           |</graphml>""".trimMargin()

    private class TrackingInputStream(content: ByteArray) : ByteArrayInputStream(content) {
        var closed: Boolean = false
            private set
        var closeCount: Int = 0
            private set
        var bytesRead: Int = 0
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            closed = true
            closeCount++
            super.close()
        }
    }
}

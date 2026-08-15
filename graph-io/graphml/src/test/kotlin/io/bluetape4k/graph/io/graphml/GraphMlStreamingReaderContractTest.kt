package io.bluetape4k.graph.io.graphml

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GraphMlStreamingReaderContractTest {

    @Test
    fun `slow collector stays within measured graphml read ahead bound`() = runSuspendIO {
        val xml = graphMl((1..10_000).joinToString("\n") { "<node id=\"v$it\"/>" })
        val input = MarkerInputStream(xml.toByteArray())

        val first = GraphMlRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(input, closeInput = true),
        ).onEach { delay(25) }.take(1).toList()

        first.single().externalId shouldBeEqualTo "v1"
        (input.markerCount <= MAX_READ_AHEAD_RECORDS).shouldBeTrue()
        (input.bytesRead < xml.toByteArray().size).shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `parse failure remains primary when owned close also fails`() = runSuspendIO {
        val input = ParseCloseFailingInputStream(
            graphMl("<node id=\"secret-record\"><data>secret-payload").toByteArray(),
        )

        val error = assertFailsWith<GraphIoReadException> {
            GraphMlRecordFlowReader().readVertices(
                GraphImportSource.InputStreamSource(input, closeInput = true),
            ).toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_VERTEX
        error.message.orEmpty() shouldNotContain "secret-record"
        error.message.orEmpty() shouldNotContain "secret-payload"
        error.suppressed.map { it.message } shouldBeEqualTo listOf("graphml-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

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

        error.failure.toString() shouldNotContain "secret-payload"
        error.failure.toString() shouldNotContain "secret-record"
        error.message.orEmpty() shouldNotContain "secret-payload"
        error.message.orEmpty() shouldNotContain "secret-record"
        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_VERTEX
    }

    @Test
    fun `sync importer reports malformed edge phase`() {
        val report = GraphMlBulkImporter().importGraph(
            source = sourceOf(
                graphMl(
                    """
                    <node id="v1"/>
                    <node id="v2"/>
                    <edge id="secret-edge" source="v1" target="v2">
                      <data key="payload">secret-payload
                    """.trimIndent(),
                ),
            ),
            operations = TinkerGraphOperations(),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.single().phase shouldBeEqualTo GraphIoPhase.READ_EDGE
    }

    @Test
    fun `suspend importer reports malformed edge phase`() = runSuspendIO {
        val report = SuspendGraphMlBulkImporter().importGraphSuspending(
            source = sourceOf(
                graphMl(
                    """
                    <node id="v1"/>
                    <node id="v2"/>
                    <edge id="secret-edge" source="v1" target="v2">
                      <data key="payload">secret-payload
                    """.trimIndent(),
                ),
            ),
            operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.single().phase shouldBeEqualTo GraphIoPhase.READ_EDGE
    }

    @Test
    fun `sync importer reports malformed edge opening phase`() {
        val report = GraphMlBulkImporter().importGraph(
            source = sourceOf(
                graphMl(
                    """
                    <edge id="secret-edge" source="v1"
                    """.trimIndent(),
                ),
            ),
            operations = TinkerGraphOperations(),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.single().phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        report.failures.single().toString() shouldNotContain "secret-edge"
    }

    @Test
    fun `suspend importer reports malformed edge opening phase`() = runSuspendIO {
        val report = SuspendGraphMlBulkImporter().importGraphSuspending(
            source = sourceOf(
                graphMl(
                    """
                    <edge id="secret-edge" source="v1"
                    """.trimIndent(),
                ),
            ),
            operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.single().phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        report.failures.single().toString() shouldNotContain "secret-edge"
    }

    @Test
    fun `streaming reader reports malformed edge phase`() = runSuspendIO {
        val error = assertFailsWith<GraphIoReadException> {
            GraphMlRecordFlowReader().readEdges(
                sourceOf(
                    graphMl(
                        """
                        <node id="v1"/>
                        <node id="v2"/>
                        <edge id="secret-edge" source="v1" target="v2">
                          <data key="payload">secret-payload
                        """.trimIndent(),
                    ),
                ),
            ).toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_EDGE
    }

    @Test
    fun `streaming reader reports malformed edge opening phase`() = runSuspendIO {
        val error = assertFailsWith<GraphIoReadException> {
            GraphMlRecordFlowReader().readEdges(
                sourceOf(
                    graphMl(
                        """
                        <edge id="secret-edge" source="v1"
                        """.trimIndent(),
                    ),
                ),
            ).toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        error.failure.toString() shouldNotContain "secret-edge"
        error.message.orEmpty() shouldNotContain "secret-edge"
    }

    @Test
    fun `streaming reader retains phase for truncated self closing edge`() = runSuspendIO {
        val error = assertFailsWith<GraphIoReadException> {
            GraphMlRecordFlowReader().readEdges(
                sourceOf(graphMl("<edge/")),
            ).toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_EDGE
    }

    @Test
    fun `streaming reader keeps vertex fallback for malformed node opening`() = runSuspendIO {
        val error = assertFailsWith<GraphIoReadException> {
            GraphMlRecordFlowReader().readVertices(
                sourceOf(
                    graphMl(
                        """
                        <node id="secret-node"
                        """.trimIndent(),
                    ),
                ),
            ).toList()
        }

        error.failure.phase shouldBeEqualTo GraphIoPhase.READ_VERTEX
        error.failure.toString() shouldNotContain "secret-node"
        error.message.orEmpty() shouldNotContain "secret-node"
    }

    @Test
    fun `bulk importer stops reading after edge buffer overflow`() {
        val trailingNodes = (1..20_000).joinToString("\n") { "<node id=\"trailing-$it\"/>" }
        val xml = graphMl(
            """
            <node id="v1"/>
            <node id="v2"/>
            <node id="v3"/>
            <edge id="e1" source="v1" target="v2"/>
            <edge id="e2" source="v2" target="v3"/>
            $trailingNodes
            """.trimIndent(),
        )
        val bytes = xml.toByteArray()
        val input = TrackingInputStream(bytes)

        val report = GraphMlBulkImporter().importGraph(
            source = GraphImportSource.InputStreamSource(input),
            operations = TinkerGraphOperations(),
            options = GraphImportOptions(maxEdgeBufferSize = 1),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.edgesRead shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 0L
        report.failures.single().message shouldBeEqualTo
            "Edge buffer exceeded maxEdgeBufferSize=1; verticesCreated=0 remain in graph as partial state"
        (input.bytesRead < bytes.size).shouldBeTrue()
    }

    @Test
    fun `suspend bulk importer stops reading after edge buffer overflow`() = runSuspendIO {
        val trailingNodes = (1..20_000).joinToString("\n") { "<node id=\"trailing-$it\"/>" }
        val xml = graphMl(
            """
            <node id="v1"/>
            <node id="v2"/>
            <node id="v3"/>
            <edge id="e1" source="v1" target="v2"/>
            <edge id="e2" source="v2" target="v3"/>
            $trailingNodes
            """.trimIndent(),
        )
        val bytes = xml.toByteArray()
        val input = TrackingInputStream(bytes)

        val report = SuspendGraphMlBulkImporter().importGraphSuspending(
            source = GraphImportSource.InputStreamSource(input, closeInput = true),
            operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
            options = GraphImportOptions(maxEdgeBufferSize = 1),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.verticesRead shouldBeEqualTo 3L
        report.verticesCreated shouldBeEqualTo 0L
        report.edgesRead shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 0L
        report.failures.single().location shouldBeEqualTo "edge-buffer:2"
        report.failures.single().message shouldBeEqualTo
            "Edge buffer exceeded maxEdgeBufferSize=1; verticesCreated=0 remain in graph as partial state"
        (input.bytesRead < bytes.size).shouldBeTrue()
        input.closed.shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `suspend bulk importer preserves source ownership and closes owned input once`() = runSuspendIO {
        val callerOwned = TrackingInputStream(graphMl("<node id=\"caller\"/>").toByteArray())
        val owned = TrackingInputStream(graphMl("<node id=\"owned\"/>").toByteArray())
        val importer = SuspendGraphMlBulkImporter()

        importer.importGraphSuspending(
            source = GraphImportSource.InputStreamSource(callerOwned),
            operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED
        importer.importGraphSuspending(
            source = GraphImportSource.InputStreamSource(owned, closeInput = true),
            operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        callerOwned.closed.shouldBeFalse()
        callerOwned.closeCount shouldBeEqualTo 0
        owned.closed.shouldBeTrue()
        owned.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `suspend bulk importer closes owned input once after real cancellation`() = runSuspendIO {
        val input = BlockingInputStream(graphMl("<node id=\"v1\"/>").toByteArray())
        val job = async(Dispatchers.IO) {
            SuspendGraphMlBulkImporter().importGraphSuspending(
                source = GraphImportSource.InputStreamSource(input, closeInput = true),
                operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
            )
        }

        try {
            input.entered.await(5, TimeUnit.SECONDS).shouldBeTrue()
            job.cancel(CancellationException("controlled-cancellation"))
            input.release.countDown()

            assertFailsWith<CancellationException> { job.await() }
        } finally {
            input.release.countDown()
            job.cancel()
            job.join()
        }

        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `suspend bulk importer preserves source cancellation and suppresses owned close failure`() = runSuspendIO {
        val cancellation = CancellationException("controlled-source-cancellation")
        val input = CancellationCloseFailingInputStream(
            content = graphMl("<node id=\"v1\"/>").toByteArray(),
            cancellation = cancellation,
        )

        val thrown = assertFailsWith<CancellationException> {
            SuspendGraphMlBulkImporter().importGraphSuspending(
                source = GraphImportSource.InputStreamSource(input, closeInput = true),
                operations = TinkerGraphSuspendOperations(TinkerGraphOperations()),
            )
        }

        thrown.message shouldBeEqualTo cancellation.message
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("graphml-close-failure")
        input.closeCount shouldBeEqualTo 1
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

    private class MarkerInputStream(private val content: ByteArray) : ByteArrayInputStream(content) {
        var bytesRead: Int = 0
            private set
        var closeCount: Int = 0
            private set
        val markerCount: Int
            get() = content.copyOf(bytesRead).decodeToString().windowed(5).count { it == "<node" }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length.coerceAtMost(1))
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            closeCount++
            super.close()
        }
    }

    private class BlockingInputStream(content: ByteArray) : ByteArrayInputStream(content) {

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var closeCount: Int = 0
            private set

        override fun read(): Int {
            awaitRelease()
            return super.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            awaitRelease()
            return super.read(buffer, offset, length)
        }

        override fun close() {
            closeCount++
            super.close()
        }

        private fun awaitRelease() {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "test input release timed out" }
        }
    }

    private class CancellationCloseFailingInputStream(
        content: ByteArray,
        private val cancellation: CancellationException,
    ) : ByteArrayInputStream(content) {

        var closeCount: Int = 0
            private set

        override fun read(): Int = throw cancellation

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw cancellation

        override fun close() {
            closeCount++
            throw IOException("graphml-close-failure")
        }
    }

    private class ParseCloseFailingInputStream(content: ByteArray) : ByteArrayInputStream(content) {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
            throw IOException("graphml-close-failure")
        }
    }

    private companion object {
        // The StAX channel currently exposes 64 records while the collector is delayed.
        const val MAX_READ_AHEAD_RECORDS = 96
    }
}

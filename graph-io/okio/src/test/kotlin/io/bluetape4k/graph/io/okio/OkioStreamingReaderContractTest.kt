package io.bluetape4k.graph.io.okio

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class OkioStreamingReaderContractTest {

    private val fakeFileSystem = FakeFileSystem()

    @AfterEach
    fun cleanup() {
        fakeFileSystem.checkNoOpenFiles()
    }

    @Test
    fun `Jackson3 reader delegates stream records in order`() = runSuspendIO {
        val input = TrackingInputStream(ndJsonVertices().toByteArray())
        val source = OkioGraphImportSource.InputStreamBased(
            input,
            ownsStream = false,
        )

        val vertices = OkioGraphRecordFlowReader(GraphIoFormat.NDJSON_JACKSON3)
            .readVertices(source)
            .toList()

        vertices.map { it.externalId } shouldBeEqualTo listOf("v1", "v2")
        input.closed.shouldBeFalse()
    }

    @Test
    fun `owned stream is closed exactly once after collection`() = runSuspendIO {
        val input = TrackingInputStream(ndJsonVertices().toByteArray())
        val source = OkioGraphImportSource.InputStreamBased(input, ownsStream = true)

        OkioGraphRecordFlowReader(GraphIoFormat.NDJSON_JACKSON3)
            .readVertices(source)
            .toList()

        input.closed.shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `slow collector stays within measured ndjson read ahead bound`() = runSuspendIO {
        val payload = generatedNdJsonVertices()
        val input = ReadAheadInputStream(payload.toByteArray(), marker = "\n")

        val first = OkioGraphRecordFlowReader(GraphIoFormat.NDJSON_JACKSON3)
            .readVertices(
                OkioGraphImportSource.InputStreamBased(input, ownsStream = true),
            )
            .onEach { delay(25) }
            .take(1)
            .toList()

        first.single().externalId shouldBeEqualTo "v1"
        (input.markerCount <= MAX_NDJSON_READ_AHEAD_RECORDS).shouldBeTrue()
        (input.bytesRead < payload.toByteArray().size).shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `slow collector stays within measured graphml read ahead bound`() = runSuspendIO {
        val payload = graphMl((1..10_000).joinToString("\n") { "<node id=\"v$it\"/>" })
        val input = ReadAheadInputStream(payload.toByteArray(), marker = "<node")

        val first = OkioGraphRecordFlowReader(GraphIoFormat.GRAPHML)
            .readVertices(
                OkioGraphImportSource.InputStreamBased(input, ownsStream = true),
            )
            .onEach { delay(25) }
            .take(1)
            .toList()

        first.single().externalId shouldBeEqualTo "v1"
        (input.markerCount <= MAX_GRAPHML_READ_AHEAD_RECORDS).shouldBeTrue()
        (input.bytesRead < payload.toByteArray().size).shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `close failure is suppressed behind parse failure`() = runSuspendIO {
        val input = FailingCloseInputStream(
            "{\"type\":\"vertex\",\"id\":\"secret-record\"\n".toByteArray(),
        )

        val error = assertFailsWith<GraphIoReadException> {
            OkioGraphRecordFlowReader(GraphIoFormat.NDJSON_JACKSON3)
                .readVertices(OkioGraphImportSource.InputStreamBased(input, ownsStream = true))
                .toList()
        }

        error.message.orEmpty() shouldNotContain "secret-record"
        error.suppressed.map { it.message } shouldBeEqualTo listOf("close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `GraphML reader preserves cancellation boundary through OkIO`() = runSuspendIO {
        val xml = graphMl((1..10_000).joinToString("\n") { "<node id=\"v$it\"/>" })
        val input = TrackingInputStream(xml.toByteArray())
        val first = OkioGraphRecordFlowReader(GraphIoFormat.GRAPHML)
            .readVertices(OkioGraphImportSource.InputStreamBased(input))
            .take(1)
            .toList()

        first.single().externalId shouldBeEqualTo "v1"
        input.closed.shouldBeFalse()
    }

    @Test
    fun `CSV reader reads paired files from PathSource`() = runSuspendIO {
        fakeFileSystem.write("/graph_vertices.csv".toPath()) {
            writeUtf8("id,label\nv1,Person\nv2,Person\n")
        }
        fakeFileSystem.write("/graph_edges.csv".toPath()) {
            writeUtf8("id,label,from,to\ne1,KNOWS,v1,v2\n")
        }
        val source = OkioGraphImportSource.PathSource("/graph.csv".toPath(), fakeFileSystem)
        val reader = OkioGraphRecordFlowReader(GraphIoFormat.CSV)

        reader.readVertices(source).toList().map { it.externalId } shouldBeEqualTo listOf("v1", "v2")
        reader.readEdges(source).toList().map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `CSV stream source fails explicitly because paired paths are required`() {
        val error = assertFailsWith<UnsupportedOperationException> {
            OkioGraphRecordFlowReader(GraphIoFormat.CSV)
                .readVertices(OkioGraphImportSource.SourceBased(Buffer()))
        }

        error.message.orEmpty().contains("PathSource").shouldBeTrue()
    }

    private fun ndJsonVertices(): String = """
        {"type":"vertex","id":"v1","label":"Person","properties":{}}
        {"type":"vertex","id":"v2","label":"Person","properties":{}}
    """.trimIndent()

    private fun generatedNdJsonVertices(): String = buildString {
        repeat(10_000) {
            val id = if (it == 0) "v1" else "trailing-v$it"
            append("{\"type\":\"vertex\",\"id\":\"$id\",\"label\":\"Person\",\"properties\":{}}\n")
        }
    }

    private fun graphMl(body: String): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<graphml xmlns="http://graphml.graphdrawing.org/graphml">
        |  <graph id="G" edgedefault="directed">
        |    $body
        |  </graph>
        |</graphml>
    """.trimMargin()

    private class TrackingInputStream(content: ByteArray) : ByteArrayInputStream(content) {
        var closed: Boolean = false
            private set
        var closeCount: Int = 0
            private set

        override fun close() {
            closed = true
            closeCount++
            super.close()
        }
    }

    private class FailingCloseInputStream(content: ByteArray) : ByteArrayInputStream(content) {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
            throw IOException("close-failure")
        }
    }

    private class ReadAheadInputStream(
        private val content: ByteArray,
        private val marker: String,
    ) : ByteArrayInputStream(content) {
        var bytesRead: Int = 0
            private set
        var closeCount: Int = 0
            private set
        val markerCount: Int
            get() = content.copyOf(bytesRead).decodeToString().windowed(marker.length).count { it == marker }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

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

    private companion object {
        // BufferedReader observed 276 records; StAX observed 64 records.
        const val MAX_NDJSON_READ_AHEAD_RECORDS = 512
        const val MAX_GRAPHML_READ_AHEAD_RECORDS = 96
    }
}

package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.assertions.shouldBeLessThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.toUtf8Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class Jackson3StreamingReaderContractTest {

    private companion object: KLoggingChannel() {
        // BufferedReader observed 276 records (8,192 bytes) before cancellation.
        const val MAX_READ_AHEAD_RECORDS = 512
    }

    @Test
    fun `slow collector stays within measured ndjson read ahead bound`() = runSuspendIO {
        val payload = generatedVertices()
        val input = MarkerInputStream(payload.toByteArray())

        val first = Jackson3NdJsonRecordFlowReader()
            .readVertices(GraphImportSource.InputStreamSource(input, closeInput = true))
            .log("Vertex")
            .onEach { delay(25.milliseconds) }
            .take(1)
            .toList()

        first.map { it.externalId } shouldBeEqualTo listOf("v0")
        input.markerCount shouldBeLessOrEqualTo MAX_READ_AHEAD_RECORDS
        input.bytesRead shouldBeLessThan payload.toByteArray().size
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `parse failure remains primary when owned close also fails`() = runSuspendIO {
        val input = CloseFailingInputStream(
            "{\"type\":\"vertex\",\"id\":\"secret-record\",\"properties\":{\n".toByteArray(),
        )

        val thrown = assertFailsWith<GraphIoReadException> {
            Jackson3NdJsonRecordFlowReader()
                .readVertices(input.asSource(true))
                .log("Vertex")
                .toList()
        }

        log.debug { "thrown=$thrown" }
        thrown.message shouldNotContain "secret-record"
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("jackson3-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `reader emits vertices and edges in source order`() = runSuspendIO {
        val source = """
            {"type":"vertex","id":"v1","label":"Person","properties":{"name":"Alice"}}
            {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
            {"type":"vertex","id":"v2","label":"Person","properties":{"name":"Bob"}}
            """.trimIndent()
            .byteInputStream()
            .asSource()

        Jackson3NdJsonRecordFlowReader().readVertices(source)
            .log("Vertex")
            .toList()
            .map { it.externalId } shouldBeEqualTo listOf("v1", "v2")

        Jackson3NdJsonRecordFlowReader()
            .readEdges(
                """
                {"type":"vertex","id":"v1","label":"Person","properties":{}}
                {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
                """.trimIndent()
                    .byteInputStream()
                    .asSource()

            )
            .log("Edge")
            .toList()
            .map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `caller owned stream remains open and owned stream closes`() = runSuspendIO {
        val callerOwned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toUtf8Bytes())
        val owned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toUtf8Bytes())

        Jackson3NdJsonRecordFlowReader.Default
            .readVertices(callerOwned.asSource())
            .log("Vertex")
            .toList()

        Jackson3NdJsonRecordFlowReader.Default
            .readVertices(owned.asSource(true))
            .log("Edge")
            .toList()

        callerOwned.closed.shouldBeFalse()
        owned.closed.shouldBeTrue()
        owned.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one emits one record before eof and closes owned streams exactly once`() = runSuspendIO {
        val vertexPayload = generatedVertices()
        val edgePayload = generatedEdges()
        val vertices = TrackingInputStream(vertexPayload.toUtf8Bytes(), maxChunk = 1)
        val edges = TrackingInputStream(edgePayload.toUtf8Bytes(), maxChunk = 1)

        val reader = Jackson3NdJsonRecordFlowReader.Default

        val firstVertex = reader
            .readVertices(vertices.asSource(true))
            .log("Vertex")
            .take(1)
            .toList()

        val firstEdge = reader
            .readEdges(edges.asSource(true))
            .log("Edge")
            .take(1)
            .toList()

        firstVertex.map { it.externalId } shouldBeEqualTo listOf("v0")
        vertices.bytesRead shouldBeLessThan vertexPayload.toUtf8Bytes().size
        vertices.closed.shouldBeTrue()
        vertices.closeCount shouldBeEqualTo 1

        firstEdge.map { it.externalId } shouldBeEqualTo listOf("e0")
        edges.bytesRead shouldBeLessThan edgePayload.toUtf8Bytes().size
        edges.closed.shouldBeTrue()
        edges.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one leaves caller owned vertex and edge streams open`() = runSuspendIO {
        val vertices = TrackingInputStream(generatedVertices().toByteArray(), maxChunk = 1)
        val edges = TrackingInputStream(generatedEdges().toByteArray(), maxChunk = 1)
        val reader = Jackson3NdJsonRecordFlowReader()

        reader.readVertices(vertices.asSource())
            .log("Vertex")
            .take(1)
            .collect()

        reader.readEdges(edges.asSource())
            .log("Edge")
            .take(1)
            .collect()

        vertices.closed.shouldBeFalse()
        vertices.closeCount shouldBeEqualTo 0
        edges.closed.shouldBeFalse()
        edges.closeCount shouldBeEqualTo 0
    }

    @Test
    fun `suspend reader rethrows source cancellation without exposing raw input`() = runSuspendIO {
        val payload = "{\"type\":\"vertex\",\"id\":\"secret-record\",\"label\":\"Person\",\"properties\":{}}\n"
        val cancellation = CancellationException("controlled-cancellation")
        val input = CancellationInputStream(
            bytes = payload.toByteArray(),
            cancelAfter = payload.indexOf('\n') + 1,
            cancellation = cancellation,
        )

        val thrown = assertFailsWith<CancellationException> {
            Jackson3NdJsonRecordFlowReader.Default
                .readVertices(input.asSource(true))
                .log("Vertex")
                .collect()
        }

        log.debug { "thrown=$thrown" }
        thrown.message shouldBeEqualTo cancellation.message
        thrown.message.orEmpty().contains("secret-record").shouldBeFalse()

        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `generated vertices remain a sequential cold flow`() = runSuspendIO {
        val content = buildString {
            repeat(10_000) {
                append("{\"type\":\"vertex\",\"id\":\"v$it\",\"label\":\"Person\",\"properties\":{}}\n")
            }
        }

        val count = Jackson3NdJsonRecordFlowReader.Default
            .readVertices(content.byteInputStream().asSource())
            .count()

        count shouldBeEqualTo 10_000
    }

    private fun generatedVertices(): String = buildString {
        repeat(10_000) {
            val id = if (it == 0) "v0" else "trailing-v$it"
            append("{\"type\":\"vertex\",\"id\":\"$id\",\"label\":\"Person\",\"properties\":{}}\n")
        }
    }

    private fun generatedEdges(): String = buildString {
        repeat(10_000) {
            val id = if (it == 0) "e0" else "trailing-e$it"
            append("{\"type\":\"edge\",\"id\":\"$id\",\"label\":\"KNOWS\",")
            append("\"from\":\"v0\",\"to\":\"v0\",\"properties\":{}}\n")
        }
    }

    private class TrackingInputStream(
        content: ByteArray,
        private val maxChunk: Int = Int.MAX_VALUE,
    ): ByteArrayInputStream(content) {
        var closed: Boolean = false
            private set
        var closeCount: Int = 0
            private set
        var bytesRead: Int = 0
            private set

        init {
            require(maxChunk > 0)
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length.coerceAtMost(maxChunk))
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            closed = true
            closeCount++
            super.close()
        }
    }

    private class MarkerInputStream(content: ByteArray): ByteArrayInputStream(content) {
        var bytesRead = 0
            private set
        var markerCount = 0
            private set

        var closeCount = 0
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                bytesRead++
                if (value.toChar() == '\n') markerCount++
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length.coerceAtMost(1))
            if (count > 0) {
                bytesRead += count
                repeat(count) { if (buffer[offset + it].toInt().toChar() == '\n') markerCount++ }
            }
            return count
        }

        override fun close() {
            closeCount++
            super.close()
        }
    }

    private class CloseFailingInputStream(content: ByteArray): ByteArrayInputStream(content) {
        var closeCount = 0
            private set

        override fun close() {
            closeCount++
            throw IOException("jackson3-close-failure")
        }
    }

    private class CancellationInputStream(
        bytes: ByteArray,
        private val cancelAfter: Int,
        private val cancellation: CancellationException,
    ): ByteArrayInputStream(bytes) {
        private var bytesRead: Int = 0
        var closeCount: Int = 0
            private set

        override fun read(): Int {
            if (bytesRead >= cancelAfter) throw cancellation
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= cancelAfter) throw cancellation
            val count = super.read(buffer, offset, length.coerceAtMost(1))
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            closeCount++
            super.close()
        }
    }
}

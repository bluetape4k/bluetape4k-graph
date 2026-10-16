package io.bluetape4k.graph.io.jackson2

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
import io.bluetape4k.support.toUtf8Bytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class Jackson2StreamingReaderContractTest {

    private companion object: KLoggingChannel() {
        // BufferedReader observed 276 records (8,192 bytes) before cancellation.
        const val MAX_READ_AHEAD_RECORDS = 512
    }

    @Test
    fun `slow collector stays within measured ndjson read ahead bound`() = runSuspendIO {
        val payload = generatedVertices()
        val input = MarkerInputStream(payload.toUtf8Bytes())

        val first = Jackson2NdJsonRecordFlowReader()
            .readVertices(input.asSource(true))
            .log("V")
            .onEach { delay(25.milliseconds) }
            .take(1)
            .toList()

        first.map { it.externalId } shouldBeEqualTo listOf("v0")
        input.markerCount shouldBeLessOrEqualTo MAX_READ_AHEAD_RECORDS
        input.bytesRead shouldBeLessThan payload.toUtf8Bytes().size
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `parse failure remains primary when owned close also fails`() = runSuspendIO {
        val input = CloseFailingInputStream(
            "{\"type\":\"vertex\",\"id\":\"secret-record\",\"properties\":{\n".toUtf8Bytes(),
        )

        val thrown = assertFailsWith<GraphIoReadException> {
            Jackson2NdJsonRecordFlowReader()
                .readVertices(input.asSource(true))
                .log("V")
                .toList()
        }

        thrown.message shouldNotContain "secret-record"
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("jackson2-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `reader emits vertices and edges in source order`() = runSuspendIO {
        val source = """
            {"type":"vertex","id":"v1","label":"Person","properties":{"name":"Alice"}}
            {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
            {"type":"vertex","id":"v2","label":"Person","properties":{"name":"Bob"}}
            """
            .trimIndent()
            .byteInputStream()
            .asSource()


        Jackson2NdJsonRecordFlowReader()
            .readVertices(source)
            .log("V")
            .toList()
            .map { it.externalId } shouldBeEqualTo listOf("v1", "v2")

        Jackson2NdJsonRecordFlowReader()
            .readEdges(
                """
                {"type":"vertex","id":"v1","label":"Person","properties":{}}
                {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
                """
                    .trimIndent()
                    .byteInputStream()
                    .asSource()
            )
            .log("E")
            .toList()
            .map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `caller owned stream remains open and owned stream closes`() = runSuspendIO {
        val callerOwned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toUtf8Bytes())
        val owned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toUtf8Bytes())

        Jackson2NdJsonRecordFlowReader()
            .readVertices(callerOwned.asSource())
            .log("V")
            .toList()

        Jackson2NdJsonRecordFlowReader()
            .readVertices(owned.asSource(true))
            .log("E")
            .toList()

        callerOwned.closed.shouldBeFalse()
        owned.closed.shouldBeTrue()
        owned.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one emits one record before eof and closes owned streams exactly once`() = runSuspendIO {
        val vertexPayload = generatedVertices()
        val edgePayload = generatedEdges()
        val vertices = TrackingInputStream(vertexPayload.toByteArray(), maxChunk = 1)
        val edges = TrackingInputStream(edgePayload.toByteArray(), maxChunk = 1)
        val reader = Jackson2NdJsonRecordFlowReader()

        val firstVertex = reader
            .readVertices(vertices.asSource(true))
            .log("V")
            .take(1)
            .toList()

        val firstEdge = reader
            .readEdges(edges.asSource(true))
            .log("E")
            .take(1)
            .toList()

        firstVertex.map { it.externalId } shouldBeEqualTo listOf("v0")
        firstEdge.map { it.externalId } shouldBeEqualTo listOf("e0")

        vertices.bytesRead shouldBeLessThan vertexPayload.toUtf8Bytes().size
        vertices.closed.shouldBeTrue()
        vertices.closeCount shouldBeEqualTo 1

        edges.bytesRead shouldBeLessThan edgePayload.toUtf8Bytes().size
        edges.closed.shouldBeTrue()
        edges.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one leaves caller owned vertex and edge streams open`() = runSuspendIO {
        val vertices = TrackingInputStream(generatedVertices().toByteArray(), maxChunk = 1)
        val edges = TrackingInputStream(generatedEdges().toByteArray(), maxChunk = 1)
        val reader = Jackson2NdJsonRecordFlowReader()

        reader.readVertices(GraphImportSource.InputStreamSource(vertices)).log("V").take(1).toList()
        reader.readEdges(GraphImportSource.InputStreamSource(edges)).log("E").take(1).toList()

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
            Jackson2NdJsonRecordFlowReader()
                .readVertices(input.asSource(true))
                .log("V")
                .toList()
        }

        thrown.message shouldBeEqualTo cancellation.message
        thrown.message shouldNotContain "secret-record"
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `generated vertices remain a sequential cold flow`() = runSuspendIO {
        val content = buildString {
            repeat(10_000) {
                append("{\"type\":\"vertex\",\"id\":\"v$it\",\"label\":\"Person\",\"properties\":{}}\n")
            }
        }

        val count = Jackson2NdJsonRecordFlowReader()
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
            throw IOException("jackson2-close-failure")
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

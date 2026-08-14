package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class Jackson2StreamingReaderContractTest {

    @Test
    fun `slow collector stays within measured ndjson read ahead bound`() = runSuspendIO {
        val payload = generatedVertices()
        val input = MarkerInputStream(payload.toByteArray())

        val first = Jackson2NdJsonRecordFlowReader()
            .readVertices(GraphImportSource.InputStreamSource(input, closeInput = true))
            .onEach { delay(25) }
            .take(1)
            .toList()

        first.map { it.externalId } shouldBeEqualTo listOf("v0")
        (input.markerCount <= MAX_READ_AHEAD_RECORDS).shouldBeTrue()
        (input.bytesRead < payload.toByteArray().size).shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `parse failure remains primary when owned close also fails`() = runSuspendIO {
        val input = CloseFailingInputStream(
            "{\"type\":\"vertex\",\"id\":\"secret-record\",\"properties\":{\n".toByteArray(),
        )

        val thrown = assertFailsWith<io.bluetape4k.graph.io.report.GraphIoReadException> {
            Jackson2NdJsonRecordFlowReader().readVertices(
                GraphImportSource.InputStreamSource(input, closeInput = true),
            ).toList()
        }

        thrown.message.orEmpty().contains("secret-record").shouldBeFalse()
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("jackson2-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `reader emits vertices and edges in source order`() = runSuspendIO {
        val source = sourceOf(
            """
            {"type":"vertex","id":"v1","label":"Person","properties":{"name":"Alice"}}
            {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
            {"type":"vertex","id":"v2","label":"Person","properties":{"name":"Bob"}}
            """.trimIndent(),
        )

        Jackson2NdJsonRecordFlowReader().readVertices(source).toList()
            .map { it.externalId } shouldBeEqualTo listOf("v1", "v2")
        Jackson2NdJsonRecordFlowReader().readEdges(
            sourceOf(
                """
                {"type":"vertex","id":"v1","label":"Person","properties":{}}
                {"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}
                """.trimIndent(),
            ),
        ).toList()
            .map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `caller owned stream remains open and owned stream closes`() = runSuspendIO {
        val callerOwned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toByteArray())
        val owned = TrackingInputStream("""{"type":"vertex","id":"v1"}""".toByteArray())

        Jackson2NdJsonRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(callerOwned),
        ).toList()
        Jackson2NdJsonRecordFlowReader().readVertices(
            GraphImportSource.InputStreamSource(owned, closeInput = true),
        ).toList()

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

        val firstVertex = reader.readVertices(
            GraphImportSource.InputStreamSource(vertices, closeInput = true),
        ).take(1).toList()
        val firstEdge = reader.readEdges(
            GraphImportSource.InputStreamSource(edges, closeInput = true),
        ).take(1).toList()

        firstVertex.map { it.externalId } shouldBeEqualTo listOf("v0")
        firstEdge.map { it.externalId } shouldBeEqualTo listOf("e0")
        (vertices.bytesRead < vertexPayload.toByteArray().size).shouldBeTrue()
        (edges.bytesRead < edgePayload.toByteArray().size).shouldBeTrue()
        vertices.closed.shouldBeTrue()
        edges.closed.shouldBeTrue()
        vertices.closeCount shouldBeEqualTo 1
        edges.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one leaves caller owned vertex and edge streams open`() = runSuspendIO {
        val vertices = TrackingInputStream(generatedVertices().toByteArray(), maxChunk = 1)
        val edges = TrackingInputStream(generatedEdges().toByteArray(), maxChunk = 1)
        val reader = Jackson2NdJsonRecordFlowReader()

        reader.readVertices(GraphImportSource.InputStreamSource(vertices)).take(1).toList()
        reader.readEdges(GraphImportSource.InputStreamSource(edges)).take(1).toList()

        vertices.closed.shouldBeFalse()
        edges.closed.shouldBeFalse()
        vertices.closeCount shouldBeEqualTo 0
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
            Jackson2NdJsonRecordFlowReader().readVertices(
                GraphImportSource.InputStreamSource(input, closeInput = true),
            ).toList()
        }

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

        val count = Jackson2NdJsonRecordFlowReader()
            .readVertices(sourceOf(content))
            .count()

        count shouldBeEqualTo 10_000
    }

    private fun sourceOf(content: String): GraphImportSource =
        GraphImportSource.InputStreamSource(content.byteInputStream())

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
    ) : ByteArrayInputStream(content) {
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

    private class MarkerInputStream(content: ByteArray) : ByteArrayInputStream(content) {
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

    private class CloseFailingInputStream(content: ByteArray) : ByteArrayInputStream(content) {
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
    ) : ByteArrayInputStream(bytes) {
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

    private companion object {
        // BufferedReader observed 276 records (8,192 bytes) before cancellation.
        const val MAX_READ_AHEAD_RECORDS = 512
    }
}

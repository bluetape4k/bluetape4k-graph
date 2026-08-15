package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.report.GraphIoReadException
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
import java.io.InputStream

class CsvStreamingReaderContractTest {

    @Test
    fun `slow collector stays within measured csv read ahead bound`() = runSuspendIO {
        val payload = generatedVertices()
        val input = MarkerInputStream(payload.toByteArray())

        val first = CsvGraphRecordFlowReader().readVertices(
            CsvGraphImportSource(
                vertices = GraphImportSource.InputStreamSource(input, closeInput = true),
                edges = GraphImportSource.InputStreamSource(ByteArrayInputStream("id,label,from,to\n".toByteArray())),
            ),
        ).onEach { delay(25) }.take(1).toList()

        first.map { it.externalId } shouldBeEqualTo listOf("v0")
        (input.markerCount <= MAX_READ_AHEAD_RECORDS).shouldBeTrue()
        (input.bytesRead < payload.toByteArray().size).shouldBeTrue()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `parse failure remains primary when owned close also fails`() = runSuspendIO {
        val input = CloseFailingInputStream("id,label\n,Person\n".toByteArray())

        val thrown = assertFailsWith<GraphIoReadException> {
            CsvGraphRecordFlowReader().readVertices(
                CsvGraphImportSource(
                    vertices = GraphImportSource.InputStreamSource(input, closeInput = true),
                    edges = GraphImportSource.InputStreamSource(
                        ByteArrayInputStream("id,label,from,to\n".toByteArray()),
                    ),
                ),
            ).toList()
        }

        thrown.message.orEmpty().contains("Person").shouldBeFalse()
        thrown.suppressed.map { it.message } shouldBeEqualTo listOf("csv-close-failure")
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `reader emits vertices and edges in source order`() = runSuspendIO {
        val reader = CsvGraphRecordFlowReader()
        val source = sourceOf(
            vertices = "id,label,prop.name\nv1,Person,Alice\nv2,Person,Bob\n",
            edges = "id,label,from,to\ne1,KNOWS,v1,v2\n",
        )

        reader.readVertices(source).toList().map { it.externalId } shouldBeEqualTo listOf("v1", "v2")
        reader.readEdges(source).toList().map { it.externalId } shouldBeEqualTo listOf("e1")
    }

    @Test
    fun `caller owned streams remain open after collection`() = runSuspendIO {
        val vertices = TrackingInputStream("id,label\nv1,Person\n".toByteArray())
        val edges = TrackingInputStream("id,label,from,to\ne1,KNOWS,v1,v1\n".toByteArray())
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(vertices),
            edges = GraphImportSource.InputStreamSource(edges),
        )

        CsvGraphRecordFlowReader().readVertices(source).toList()
        CsvGraphRecordFlowReader().readEdges(source).toList()

        vertices.closed.shouldBeFalse()
        edges.closed.shouldBeFalse()
    }

    @Test
    fun `owned streams close after collection`() = runSuspendIO {
        val vertices = TrackingInputStream("id,label\nv1,Person\n".toByteArray())
        val edges = TrackingInputStream("id,label,from,to\ne1,KNOWS,v1,v1\n".toByteArray())
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(vertices, closeInput = true),
            edges = GraphImportSource.InputStreamSource(edges, closeInput = true),
        )

        CsvGraphRecordFlowReader().readVertices(source).toList()
        CsvGraphRecordFlowReader().readEdges(source).toList()

        vertices.closed.shouldBeTrue()
        edges.closed.shouldBeTrue()
        vertices.closeCount shouldBeEqualTo 1
        edges.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `take one emits one record before eof and closes owned streams exactly once`() = runSuspendIO {
        val vertexPayload = generatedVertices()
        val edgePayload = generatedEdges()
        val vertices = TrackingInputStream(vertexPayload.toByteArray(), maxChunk = 1)
        val edges = TrackingInputStream(edgePayload.toByteArray(), maxChunk = 1)
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(vertices, closeInput = true),
            edges = GraphImportSource.InputStreamSource(edges, closeInput = true),
        )
        val reader = CsvGraphRecordFlowReader()

        val firstVertex = reader.readVertices(source).take(1).toList()
        val firstEdge = reader.readEdges(source).take(1).toList()

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
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(vertices),
            edges = GraphImportSource.InputStreamSource(edges),
        )
        val reader = CsvGraphRecordFlowReader()

        reader.readVertices(source).take(1).toList()
        reader.readEdges(source).take(1).toList()

        vertices.closed.shouldBeFalse()
        edges.closed.shouldBeFalse()
        vertices.closeCount shouldBeEqualTo 0
        edges.closeCount shouldBeEqualTo 0
    }

    @Test
    fun `suspend reader rethrows source cancellation without exposing raw input`() = runSuspendIO {
        val cancellation = CancellationException("controlled-cancellation")
        val input = CancellationInputStream(
            bytes = "id,label\nsecret-record,Person\n".toByteArray(),
            cancelAfter = 12,
            cancellation = cancellation,
        )

        val thrown = assertFailsWith<CancellationException> {
            CsvGraphRecordFlowReader().readVertices(
                CsvGraphImportSource(
                    vertices = GraphImportSource.InputStreamSource(input, closeInput = true),
                    edges = GraphImportSource.InputStreamSource(
                        ByteArrayInputStream("id,label,from,to\n".toByteArray()),
                    ),
                ),
            ).toList()
        }

        thrown.message shouldBeEqualTo cancellation.message
        thrown.message.orEmpty().contains("secret-record").shouldBeFalse()
        input.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `generated vertices remain a sequential cold flow`() = runSuspendIO {
        val vertices = buildString {
            append("id,label\n")
            repeat(10_000) { append("v$it,Person\n") }
        }
        val count = CsvGraphRecordFlowReader().readVertices(
            sourceOf(vertices, "id,label,from,to\n"),
        ).count()

        count shouldBeEqualTo 10_000
    }

    @Test
    fun `malformed vertex row fails with safe location`() = runSuspendIO {
        val source = sourceOf(
            vertices = "id,label\n,Person\n",
            edges = "id,label,from,to\n",
        )

        val error = assertFailsWith<GraphIoReadException> {
            CsvGraphRecordFlowReader().readVertices(source).toList()
        }

        error.failure.location shouldBeEqualTo "row:1"
        error.message.orEmpty().contains("Person").shouldBeFalse()
    }

    @Test
    fun `fatal input errors are not converted to malformed csv`() = runSuspendIO {
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(FatalInputStream()),
            edges = GraphImportSource.InputStreamSource(ByteArrayInputStream("id,label\n".toByteArray())),
        )

        assertFailsWith<AssertionError> {
            CsvGraphRecordFlowReader().readVertices(source).toList()
        }
    }

    @Test
    fun `owned stream close failures remain infrastructure failures`() = runSuspendIO {
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(
                CloseFailingInputStream("id,label\nv1,Person\n".toByteArray()),
                closeInput = true,
            ),
            edges = GraphImportSource.InputStreamSource(ByteArrayInputStream("id,label,from,to\n".toByteArray())),
        )

        assertFailsWith<IOException> {
            CsvGraphRecordFlowReader().readVertices(source).toList()
        }
    }

    private fun sourceOf(vertices: String, edges: String): CsvGraphImportSource = CsvGraphImportSource(
        vertices = GraphImportSource.InputStreamSource(ByteArrayInputStream(vertices.toByteArray())),
        edges = GraphImportSource.InputStreamSource(ByteArrayInputStream(edges.toByteArray())),
    )

    private fun generatedVertices(): String = buildString {
        append("id,label\n")
        append("v0,Person\n")
        repeat(10_000) { append("trailing-v$it,Person\n") }
    }

    private fun generatedEdges(): String = buildString {
        append("id,label,from,to\n")
        append("e0,KNOWS,v0,v0\n")
        repeat(10_000) { append("trailing-e$it,KNOWS,v0,v0\n") }
    }

    private class TrackingInputStream(
        bytes: ByteArray,
        private val maxChunk: Int = Int.MAX_VALUE,
    ) : ByteArrayInputStream(bytes) {
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

    private class MarkerInputStream(private val content: ByteArray) : ByteArrayInputStream(content) {
        var bytesRead: Int = 0
            private set
        var closeCount: Int = 0
            private set
        val markerCount: Int
            get() = content.copyOf(bytesRead).count { it == '\n'.code.toByte() } - 1

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

    private class FatalInputStream : InputStream() {
        override fun read(): Int = throw AssertionError("fatal-csv-input")
    }

    private class CloseFailingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount++
            throw IOException("csv-close-failure")
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
        // Observed with a 1-byte source and a 25 ms collector delay; keep room for
        // parser implementation variance without permitting unbounded prefetch.
        const val MAX_READ_AHEAD_RECORDS = 8
    }
}

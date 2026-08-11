package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class CsvStreamingReaderContractTest {

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

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
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

    private class FatalInputStream : InputStream() {
        override fun read(): Int = throw AssertionError("fatal-csv-input")
    }

    private class CloseFailingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        override fun close() {
            throw IOException("csv-close-failure")
        }
    }
}

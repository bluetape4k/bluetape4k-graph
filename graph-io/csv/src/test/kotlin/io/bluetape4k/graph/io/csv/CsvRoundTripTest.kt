package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoProgressEvent
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class CsvRoundTripTest {

    companion object: KLogging()

    @Test
    fun `round trip two vertices and one edge`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val source = TinkerGraphOperations()
        val alice = source.createVertex("Person", mapOf("name" to "Alice"))
        val bob = source.createVertex("Person", mapOf("name" to "Bob"))
        source.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2024"))
        val events = mutableListOf<GraphIoProgressEventType>()
        val snapshots = mutableListOf<GraphIoProgressEvent>()

        val exporter = CsvGraphBulkExporter()
        exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
            GraphIoProgressListener {
                events += it.type
                snapshots += it
            },
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
        snapshots.last().bytesProcessed shouldBeEqualTo (Files.size(vOut) + Files.size(eOut))
        snapshots.last().bytesTotal shouldBeEqualTo (Files.size(vOut) + Files.size(eOut))

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val report = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(
                onDuplicateVertexId = DuplicateVertexPolicy.FAIL,
                onMissingEdgeEndpoint = MissingEndpointPolicy.FAIL,
            ),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
        Files.size(vOut) shouldBeGreaterThan 0L
    }

    @Test
    fun `sync export uses bounded chunks once per label and unions properties`(@TempDir dir: Path) {
        val vOut = dir.resolve("bounded-v.csv")
        val eOut = dir.resolve("bounded-e.csv")
        val source = TinkerGraphOperations()
        val person = source.createVertex("Person", mapOf("name" to "Alice"))
        val company = source.createVertex("Company", mapOf("industry" to "Software"))
        source.createEdge(person.id, company.id, "WORKS", mapOf("since" to 2024))
        val requests = mutableListOf<String>()

        val report = CsvGraphBulkExporter().exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            ChunkOnlyGraphOperations(source, requests),
            GraphExportOptions(
                vertexLabels = setOf("Person", "Company"),
                edgeLabels = setOf("WORKS"),
                exportChunkSize = 1,
            ),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
        report.edgesWritten shouldBeEqualTo 1L
        requests.sorted() shouldBeEqualTo listOf("Company:1", "Person:1", "WORKS:1")
        Files.readString(vOut) shouldContain "prop.industry"
        Files.readString(vOut) shouldContain "prop.name"
    }

    @Test
    fun `sync export freezes the first chunk before backend mutation`(@TempDir dir: Path) {
        val vOut = dir.resolve("mutation-v.csv")
        val eOut = dir.resolve("mutation-e.csv")
        val vertexProperties = linkedMapOf<String, Any?>("name" to "before")
        val edgeProperties = linkedMapOf<String, Any?>("state" to "before")
        val backend = MutatingChunkOnlyGraphOperations(
            vertex = GraphVertex(GraphElementId.of("v-1"), "Person", vertexProperties),
            edge = GraphEdge(
                GraphElementId.of("e-1"),
                "KNOWS",
                GraphElementId.of("v-1"),
                GraphElementId.of("v-2"),
                edgeProperties,
            ),
            vertexProperties = vertexProperties,
            edgeProperties = edgeProperties,
        )

        val report = CsvGraphBulkExporter().exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            backend,
            GraphExportOptions(
                vertexLabels = setOf("Person"),
                edgeLabels = setOf("KNOWS"),
                exportChunkSize = 1,
            ),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 1L
        report.edgesWritten shouldBeEqualTo 1L
        backend.requests shouldBeEqualTo listOf("vertices:1", "edges:1")
        Files.readString(vOut).also {
            it shouldContain "before"
            it shouldNotContain "after"
        }
        Files.readString(eOut).also {
            it shouldContain "before"
            it shouldNotContain "after"
        }
    }

    @Test
    fun `sync export preserves caller-owned output streams`() {
        val vertices = TrackingOutputStream()
        val edges = TrackingOutputStream()

        CsvGraphBulkExporter().exportGraph(
            CsvGraphExportSink(
                GraphExportSink.OutputStreamSink(vertices, closeOutput = false),
                GraphExportSink.OutputStreamSink(edges, closeOutput = false),
            ),
            TinkerGraphOperations(),
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        )

        vertices.closed shouldBeEqualTo false
        edges.closed shouldBeEqualTo false
        vertices.write('x'.code)
        edges.write('x'.code)
    }

    @Test
    fun `sync export preserves the primary sink failure while closing spool`() {
        val source = TinkerGraphOperations().also {
            it.createVertex("Person", mapOf("name" to "Alice"))
        }

        val thrown = assertFailsWith<IOException> {
            CsvGraphBulkExporter().exportGraph(
                CsvGraphExportSink(
                    GraphExportSink.OutputStreamSink(FailingOutputStream("csv-sink-failure")),
                    GraphExportSink.OutputStreamSink(ByteArrayOutputStream()),
                ),
                source,
                GraphExportOptions(vertexLabels = setOf("Person")),
            )
        }

        thrown.message shouldBeEqualTo "csv-sink-failure"
    }

    private class ChunkOnlyGraphOperations(
        private val delegate: GraphOperations,
        private val requests: MutableList<String>,
    ) : GraphOperations by delegate {

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            error("full vertex list lookup must not be used by CSV export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            error("full edge list lookup must not be used by CSV export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphVertex>> {
            requests += "$label:$chunkSize"
            return delegate.findVerticesByLabelChunked(label, filter, chunkSize)
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphEdge>> {
            requests += "$label:$chunkSize"
            return delegate.findEdgesByLabelChunked(label, filter, chunkSize)
        }
    }

    private class MutatingChunkOnlyGraphOperations(
        private val vertex: GraphVertex,
        private val edge: GraphEdge,
        private val vertexProperties: MutableMap<String, Any?>,
        private val edgeProperties: MutableMap<String, Any?>,
    ) : GraphOperations by TinkerGraphOperations() {

        val requests = mutableListOf<String>()

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            error("full vertex list lookup must not be used by CSV export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            error("full edge list lookup must not be used by CSV export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphVertex>> = sequence {
            requests += "vertices:$chunkSize"
            yield(listOf(vertex))
            vertexProperties["name"] = "after"
            yield(emptyList())
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphEdge>> = sequence {
            requests += "edges:$chunkSize"
            yield(listOf(edge))
            edgeProperties["state"] = "after"
            yield(emptyList())
        }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed: Boolean = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FailingOutputStream(
        private val message: String,
    ) : OutputStream() {
        override fun write(b: Int): Unit = throw IOException(message)

        override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException(message)
    }
}

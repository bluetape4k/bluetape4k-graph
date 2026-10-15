package io.bluetape4k.graph.io.graphml

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoProgressEvent
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLStreamException
import kotlin.io.path.writeText

class GraphMlRoundTripTest {

    companion object: KLogging()

    @Test
    fun `sync round trip three vertices two edges`(@TempDir dir: Path) {
        val out = dir.resolve("graph.graphml")

        val src = TinkerGraphOperations()
        val alice = src.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
        val bob = src.createVertex("Person", mapOf("name" to "Bob", "age" to 25))
        val charlie = src.createVertex("Person", mapOf("name" to "Charlie", "age" to 22))
        src.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2020"))
        src.createEdge(bob.id, charlie.id, "KNOWS", mapOf("since" to "2022"))
        val events = mutableListOf<GraphIoProgressEventType>()
        val snapshots = mutableListOf<GraphIoProgressEvent>()

        val exporter = GraphMlBulkExporter()
        exporter.exportGraph(
            out.asSink(),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ) {
            events += it.type
            snapshots += it
        }.status shouldBeEqualTo GraphIoStatus.COMPLETED

        log.debug { "events=$events" }

        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
        snapshots.last().bytesProcessed shouldBeEqualTo Files.size(out)
        snapshots.last().bytesTotal shouldBeEqualTo Files.size(out)

        val target = TinkerGraphOperations()
        val importer = GraphMlBulkImporter()
        val report = importer.importGraph(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `sync round trip preserves supported property types`(@TempDir dir: Path) {
        val out = dir.resolve("typed.graphml")

        val src = TinkerGraphOperations()
        val firstProperties = mapOf(
            "price" to 9.99,
            "stock" to 100,
            "sequence" to 1L,
            "ratio" to 0.5F,
            "active" to true,
            "name" to "first",
        )
        val secondProperties = mapOf(
            "price" to 4.5,
            "stock" to 50,
            "sequence" to 2L,
            "ratio" to 0.25F,
            "active" to false,
            "name" to "second",
        )
        val edgeProperties = mapOf("weight" to 0.75, "enabled" to true)
        val n1 = src.createVertex("Item", firstProperties)
        val n2 = src.createVertex("Item", secondProperties)
        src.createEdge(n1.id, n2.id, "RELATED", edgeProperties)

        GraphMlBulkExporter().exportGraph(
            out.asSink(),
            src,
            GraphExportOptions(vertexLabels = setOf("Item"), edgeLabels = setOf("RELATED")),
        )

        val target = TinkerGraphOperations()
        val report = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L

        target.findVerticesByLabel("Item")
            .map { vertex ->
                vertex.properties.filterKeys { it != "_graphIoExternalId" }
            }
            .toSet() shouldBeEqualTo setOf(firstProperties, secondProperties)
        target.findEdgesByLabel("RELATED").single().properties
            .filterKeys { it != "_graphIoExternalId" } shouldBeEqualTo edgeProperties
    }

    @Test
    fun `sync export uses chunked repository API without full label materialization`(@TempDir dir: Path) {
        val out = dir.resolve("chunked.graphml")
        val src = TinkerGraphOperations()
        val vertices = List(5) { index ->
            src.createVertex("Person", "name" to "Person-$index")
        }
        src.createEdge(vertices[0].id, vertices[1].id, "KNOWS", "rank" to 1)
        src.createEdge(vertices[1].id, vertices[2].id, "KNOWS", "rank" to 2)
        val requestedChunkSizes = mutableListOf<Int>()

        val report = GraphMlBulkExporter().exportGraph(
            out.asSink(),
            ChunkOnlyGraphOperations(src, requestedChunkSizes),
            GraphExportOptions(
                vertexLabels = setOf("Person"),
                edgeLabels = setOf("KNOWS"),
                exportChunkSize = 2,
            ),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 5L
        report.edgesWritten shouldBeEqualTo 2L
        requestedChunkSizes shouldBeEqualTo listOf(2, 2)
    }

    @Test
    fun `sync export honors empty property option`(@TempDir dir: Path) {
        val source = NullPropertyGraphOperations()

        listOf(true, false).forEach { includeEmptyProperties ->
            val out = dir.resolve("empty-$includeEmptyProperties.graphml")
            GraphMlBulkExporter().exportGraph(
                out.asSink(),
                source,
                GraphExportOptions(
                    vertexLabels = setOf("Metric"),
                    edgeLabels = setOf("NONE"),
                    includeEmptyProperties = includeEmptyProperties,
                ),
            )
            val xml = Files.readString(out)
            val keyId = Regex("""<key id="([^"]+)" for="node" attr.name="optional"""")
                .find(xml)
                ?.groupValues
                ?.get(1)
                .shouldNotBeNull()

            xml.contains("<data key=\"$keyId\">") shouldBeEqualTo includeEmptyProperties
        }
    }

    @Test
    fun `sync export rejects mixed types across repository chunks before opening sink`(@TempDir dir: Path) {
        val out = dir.resolve("mixed.graphml")
        val source = TinkerGraphOperations().also {
            it.createVertex("Metric", mapOf("rank" to 1))
            it.createVertex("Metric", mapOf("rank" to 2L))
        }

        assertFailsWith<IllegalArgumentException> {
            GraphMlBulkExporter().exportGraph(
                out.asSink(),
                source,
                GraphExportOptions(vertexLabels = setOf("Metric"), exportChunkSize = 1),
            )
        }

        Files.notExists(out).shouldBeTrue()
    }

    @Test
    fun `sync export freezes the first chunk before backend mutation`(@TempDir dir: Path) {
        val out = dir.resolve("mutation.graphml")
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

        val report = GraphMlBulkExporter().exportGraph(
            out.asSink(),
            backend,
            GraphExportOptions(
                vertexLabels = setOf("Person"),
                edgeLabels = setOf("KNOWS"),
                exportChunkSize = 1,
            ),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 1L
        report.edgesWritten shouldBeEqualTo 1L
        backend.requests shouldBeEqualTo listOf("vertices:1", "edges:1")

        Files.readString(out).also {
            it shouldContain "before"
            it shouldNotContain "after"
        }
    }

    @Test
    fun `sync export preserves the primary sink failure while closing spool`() {
        val source = TinkerGraphOperations().also {
            it.createVertex("Person", mapOf("name" to "Alice"))
        }

        val thrown = assertFailsWith<XMLStreamException> {
            GraphMlBulkExporter().exportGraph(
                GraphExportSink.OutputStreamSink(FailingOutputStream("graphml-sink-failure")),
                source,
                GraphExportOptions(vertexLabels = setOf("Person")),
            )
        }

        generateSequence<Throwable>(thrown) { it.cause }
            .any { it.message?.contains("graphml-sink-failure") == true }
            .shouldBeTrue()
    }

    @Test
    fun `duplicate vertex skip policy records partial report failure`(@TempDir dir: Path) {
        val graphml = dir.resolve("duplicate.graphml").also {
            it.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/graphml">
                  <graph id="G" edgedefault="directed">
                    <node id="n1"/>
                    <node id="n1"/>
                  </graph>
                </graphml>""".trimIndent(),
            )
        }

        val report = GraphMlBulkImporter().importGraph(
            graphml.asSource(),
            TinkerGraphOperations(),
            GraphImportOptions(onDuplicateVertexId = DuplicateVertexPolicy.SKIP),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.verticesRead shouldBeEqualTo 2L
        report.verticesCreated shouldBeEqualTo 1L
        report.skippedVertices shouldBeEqualTo 1L
        report.failures shouldHaveSize 1
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
        report.failures.single().message shouldContain "Duplicate vertex skipped"
    }

    @Test
    fun `missing endpoint skip policy records partial report failure`(@TempDir dir: Path) {
        val graphml = dir.resolve("missing-endpoint.graphml").also {
            it.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/graphml">
                  <graph id="G" edgedefault="directed">
                    <node id="n1"/>
                    <edge id="e1" source="n1" target="missing"/>
                  </graph>
                </graphml>""".trimIndent(),
            )
        }

        val report = GraphMlBulkImporter().importGraph(
            graphml.asSource(),
            TinkerGraphOperations(),
            GraphImportOptions(onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.verticesCreated shouldBeEqualTo 1L
        report.edgesRead shouldBeEqualTo 1L
        report.edgesCreated shouldBeEqualTo 0L
        report.skippedEdges shouldBeEqualTo 1L
        report.failures shouldHaveSize 1
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
        report.failures.single().message shouldContain "Missing endpoint skipped"
    }

    @Test
    fun `unsupported port fail policy returns failed report without creating elements`(@TempDir dir: Path) {
        val graphml = dir.resolve("unsupported-port.graphml").also {
            it.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/graphml">
                  <graph id="G" edgedefault="directed">
                    <node id="n1">
                      <port name="p1"/>
                    </node>
                  </graph>
                </graphml>""".trimIndent(),
            )
        }

        val report = GraphMlBulkImporter().importGraph(
            graphml.asSource(),
            TinkerGraphOperations(),
            GraphImportOptions(),
            GraphMlImportOptions(unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.verticesRead shouldBeEqualTo 1L
        report.verticesCreated shouldBeEqualTo 0L
        report.failures shouldHaveSize 1
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.ERROR
        report.failures.single().elementName shouldBeEqualTo "port"
    }

    private class ChunkOnlyGraphOperations(
        private val delegate: GraphOperations,
        private val requestedChunkSizes: MutableList<Int>,
    ): GraphOperations by delegate {

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            error("full vertex list lookup must not be used by GraphML export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            error("full edge list lookup must not be used by GraphML export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphVertex>> {
            requestedChunkSizes += chunkSize
            return delegate.findVerticesByLabelChunked(label, filter, chunkSize)
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphEdge>> {
            requestedChunkSizes += chunkSize
            return delegate.findEdgesByLabelChunked(label, filter, chunkSize)
        }
    }

    private class NullPropertyGraphOperations: GraphOperations by TinkerGraphOperations() {
        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphVertex>> = sequenceOf(
            listOf(GraphVertex(GraphElementId.of("v-1"), label, mapOf("optional" to null))),
        )
    }

    private class MutatingChunkOnlyGraphOperations(
        private val vertex: GraphVertex,
        private val edge: GraphEdge,
        private val vertexProperties: MutableMap<String, Any?>,
        private val edgeProperties: MutableMap<String, Any?>,
    ): GraphOperations by TinkerGraphOperations() {

        val requests = mutableListOf<String>()

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            error("full vertex list lookup must not be used by GraphML export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            error("full edge list lookup must not be used by GraphML export")

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

    private class FailingOutputStream(
        private val message: String,
    ): OutputStream() {
        override fun write(b: Int): Unit = throw IOException(message)

        override fun write(b: ByteArray, off: Int, len: Int): Unit = throw IOException(message)
    }
}

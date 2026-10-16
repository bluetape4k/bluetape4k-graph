package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Jackson3RoundTripTest {

    companion object: KLogging()

    @Test
    fun `sync round trip three vertices two edges`(@TempDir dir: Path) {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphOperations()
        val alice = src.createVertex("Person", "name" to "Alice")
        val bob = src.createVertex("Person", "name" to "Bob")
        val charlie = src.createVertex("Person", "name" to "Charlie")
        src.createEdge(alice.id, bob.id, "KNOWS", "since" to "2020")
        src.createEdge(bob.id, charlie.id, "KNOWS", "since" to "2022")

        val events = mutableListOf<GraphIoProgressEventType>()

        val exporter = Jackson3NdJsonBulkExporter()
        exporter.exportGraph(
            out.asSink(),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ) { events += it.type }.status shouldBeEqualTo GraphIoStatus.COMPLETED

        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )

        val target = TinkerGraphOperations()
        val importer = Jackson3NdJsonBulkImporter()
        val report = importer.importGraph(
            out.asSource(),
            target,
            GraphImportOptions(),
        )
        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `sync export uses chunked repository API instead of full label list`(@TempDir dir: Path) {
        val out = dir.resolve("graph.ndjson")
        val src = TinkerGraphOperations()
        val vertices = (1..5).map { index ->
            src.createVertex("Person", "name" to "Person-$index")
        }
        src.createEdge(vertices[0].id, vertices[1].id, "KNOWS", "rank" to 1)
        src.createEdge(vertices[1].id, vertices[2].id, "KNOWS", "rank" to 2)

        val report = Jackson3NdJsonBulkExporter().exportGraph(
            out.asSink(),
            ChunkOnlyGraphOperations(src),
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS"), exportChunkSize = 2),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 5L
        report.edgesWritten shouldBeEqualTo 2L
    }

    private class ChunkOnlyGraphOperations(
        private val delegate: GraphOperations,
    ): GraphOperations by delegate {
        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            error("full vertex list lookup must not be used by Jackson3 export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            error("full edge list lookup must not be used by Jackson3 export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphVertex>> =
            delegate.findVerticesByLabelChunked(label, filter, chunkSize)

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Sequence<List<GraphEdge>> =
            delegate.findEdgesByLabelChunked(label, filter, chunkSize)
    }
}

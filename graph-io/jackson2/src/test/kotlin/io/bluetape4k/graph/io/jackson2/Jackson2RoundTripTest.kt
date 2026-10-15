package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoProgressEvent
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
import java.nio.file.Files
import java.nio.file.Path

class Jackson2RoundTripTest {

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
        val snapshots = mutableListOf<GraphIoProgressEvent>()

        val exporter = Jackson2NdJsonBulkExporter()
        exporter.exportGraph(
            out.asSink(),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ) {
            events += it.type
            snapshots += it
        }.status shouldBeEqualTo GraphIoStatus.COMPLETED

        events.forEach { log.debug { "event=$it" } }
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
        val importer = Jackson2NdJsonBulkImporter.Default
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
    fun `sync export uses chunked repository API with requested chunk size`(@TempDir dir: Path) {
        val out = dir.resolve("graph.ndjson")
        val src = TinkerGraphOperations()

        val vertices = List(5) { index ->
            src.createVertex("Person", "name" to "Person-$index")
        }
        src.createEdge(vertices[0].id, vertices[1].id, "KNOWS", "rank" to 1)
        src.createEdge(vertices[1].id, vertices[2].id, "KNOWS", "rank" to 2)

        val requestedChunkSizes = mutableListOf<Int>()

        val report = Jackson2NdJsonBulkExporter.Default
            .exportGraph(
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

    private class ChunkOnlyGraphOperations(
        private val delegate: GraphOperations,
        private val requestedChunkSizes: MutableList<Int>,
    ): GraphOperations by delegate {

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            error("full vertex list lookup must not be used by Jackson2 export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            error("full edge list lookup must not be used by Jackson2 export")

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
}

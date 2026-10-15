package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Jackson2SuspendTest {

    companion object: KLoggingChannel()

    @Test
    fun `suspend round trip`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphSuspendOperations()
        val alice = src.createVertex("Person", "name" to "Alice")
        val bob = src.createVertex("Person", "name" to "Bob")
        src.createEdge(alice.id, bob.id, "KNOWS")

        SuspendJackson2NdJsonBulkExporter()
            .exportGraphSuspending(
                out.asSink(),
                src,
                GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
            )
            .status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = SuspendJackson2NdJsonBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(out),
            TinkerGraphSuspendOperations(),
            GraphImportOptions(),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `suspend export uses chunked repository API with requested chunk size`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("graph.ndjson")
        val src = TinkerGraphSuspendOperations()
        val vertices = List(5) { index ->
            src.createVertex("Person", "name" to "Person-$index")
        }
        src.createEdge(vertices[0].id, vertices[1].id, "KNOWS", "rank" to 1)
        src.createEdge(vertices[1].id, vertices[2].id, "KNOWS", "rank" to 2)

        val requestedChunkSizes = mutableListOf<Int>()

        val report = SuspendJackson2NdJsonBulkExporter().exportGraphSuspending(
            out.asSink(),
            ChunkOnlyGraphSuspendOperations(src, requestedChunkSizes),
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
    fun `suspend importer reports invalid vertex envelope`(@TempDir dir: Path) = runTest {
        val input = dir.resolve("invalid-vertex.ndjson")
        Files.writeString(input, """{"type":"vertex","label":"Person"}""" + "\n")

        val report = SuspendJackson2NdJsonBulkImporter().importGraphSuspending(
            input.asSource(),
            TinkerGraphSuspendOperations(),
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.single().location shouldBeEqualTo "line:1"
        report.failures.single().message shouldContain "missing id"
    }

    private class ChunkOnlyGraphSuspendOperations(
        private val delegate: GraphSuspendOperations,
        private val requestedChunkSizes: MutableList<Int>,
    ): GraphSuspendOperations by delegate {

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> =
            error("full vertex Flow lookup must not be used by Jackson2 export")

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
            error("full edge Flow lookup must not be used by Jackson2 export")

        override fun findVerticesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphVertex>> {
            requestedChunkSizes += chunkSize
            return delegate.findVerticesByLabelChunked(label, filter, chunkSize)
        }

        override fun findEdgesByLabelChunked(
            label: String,
            filter: Map<String, Any?>,
            chunkSize: Int,
        ): Flow<List<GraphEdge>> {
            requestedChunkSizes += chunkSize
            return delegate.findEdgesByLabelChunked(label, filter, chunkSize)
        }
    }
}

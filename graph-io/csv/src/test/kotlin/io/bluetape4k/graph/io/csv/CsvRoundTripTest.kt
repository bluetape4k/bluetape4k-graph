package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CsvRoundTripTest {

    @Test
    fun `round trip two vertices and one edge`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val source = TinkerGraphOperations()
        val alice = source.createVertex("Person", mapOf("name" to "Alice"))
        val bob = source.createVertex("Person", mapOf("name" to "Bob"))
        source.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2024"))

        val exporter = CsvGraphBulkExporter()
        exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

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
}

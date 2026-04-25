package io.bluetape4k.graph.io.cross

import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CrossFormatCsvTest {

    @Test
    fun `CSV export and import round trip with 10 vertices and 10 edges`(@TempDir dir: Path) {
        val vOut = dir.resolve("vertices.csv")
        val eOut = dir.resolve("edges.csv")

        val src = TinkerGraphOperations()

        val vertices = (1..10).map { i ->
            src.createVertex("Person", mapOf("name" to "Person$i", "age" to (20 + i)))
        }

        (0 until 10).forEach { i ->
            val from = vertices[i]
            val to = vertices[(i + 1) % 10]
            src.createEdge(from.id, to.id, "KNOWS", mapOf("since" to "200${i}"))
        }

        val exporter = CsvGraphBulkExporter()
        exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val report = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 10L
        report.edgesCreated shouldBeEqualTo 10L
        target.findVerticesByLabel("Person").size shouldBeEqualTo 10
    }
}

package io.bluetape4k.graph.io.cross

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.random.Random

class CrossFormatCsvTest {

    companion object: KLogging()

    @Test
    fun `CSV export and import round trip with 10 vertices and 10 edges`(@TempDir dir: Path) {
        val vertexCount = 100
        val edgeCount = 100

        val vOut = dir.resolve("vertices.csv")
        val eOut = dir.resolve("edges.csv")

        val src = TinkerGraphOperations()

        val vertices = List(vertexCount) { i ->
            src.createVertex("Person", "name" to "Person$i", "age" to Random.nextInt(20, 90))
        }

        repeat(edgeCount) { i ->
            val from = vertices[i]
            val to = vertices[(i + 1) % vertexCount]
            src.createEdge(from.id, to.id, "KNOWS", "since" to "200${i}")
        }

        val exporter = CsvGraphBulkExporter()
        exporter.exportGraph(
            CsvGraphExportSink(vOut.asSink(), eOut.asSink()),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val report = importer.importGraph(
            CsvGraphImportSource(vOut.asSource(), eOut.asSource()),
            target,
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo vertexCount.toLong()
        report.edgesCreated shouldBeEqualTo edgeCount.toLong()
        target.findVerticesByLabel("Person").size shouldBeEqualTo vertexCount
    }
}

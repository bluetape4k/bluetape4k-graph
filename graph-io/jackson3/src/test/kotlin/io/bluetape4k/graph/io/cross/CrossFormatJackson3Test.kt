package io.bluetape4k.graph.io.cross

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CrossFormatJackson3Test {

    companion object: KLogging()

    @Test
    fun `Jackson3 NdJson export and import round trip with 10 vertices and 10 edges`(@TempDir dir: Path) {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphOperations()

        val vertices = List(10) { i ->
            src.createVertex("Person", "name" to "Person$i", "age" to (20 + i))
        }

        repeat(10) { i ->
            val from = vertices[i]
            val to = vertices[(i + 1) % 10]
            src.createEdge(from.id, to.id, "KNOWS", "since" to "200${i}")
        }

        val exporter = Jackson3NdJsonBulkExporter.Default

        val exportResult = exporter.exportGraph(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        )
        log.debug { "exportResult=$exportResult" }
        exportResult.status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = Jackson3NdJsonBulkImporter()
        val importReport = importer.importGraph(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        )
        log.debug { "importReport=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 10L
        importReport.edgesCreated shouldBeEqualTo 10L

        target.findVerticesByLabel("Person").size shouldBeEqualTo 10
    }
}

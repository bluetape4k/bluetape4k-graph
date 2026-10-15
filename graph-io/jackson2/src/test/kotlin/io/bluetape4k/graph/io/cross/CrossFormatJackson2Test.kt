package io.bluetape4k.graph.io.cross

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CrossFormatJackson2Test {

    companion object: KLogging()

    @Test
    fun `Jackson2 NdJson export and import round trip with 10 vertices and 10 edges`(@TempDir dir: Path) {
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

        val exporter = Jackson2NdJsonBulkExporter.Default
        exporter.exportGraph(
            out.asSink(),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = Jackson2NdJsonBulkImporter.Default
        val report = importer.importGraph(
            out.asSource(),
            target,
            GraphImportOptions(),
        )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 10L
        report.edgesCreated shouldBeEqualTo 10L
        target.findVerticesByLabel("Person").size shouldBeEqualTo 10
    }
}

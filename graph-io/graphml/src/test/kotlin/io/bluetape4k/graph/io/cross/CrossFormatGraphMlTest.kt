package io.bluetape4k.graph.io.cross

import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
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

class CrossFormatGraphMlTest {

    @Test
    fun `GraphML export and import round trip with 10 vertices and 10 edges`(@TempDir dir: Path) {
        val out = dir.resolve("graph.graphml")

        val src = TinkerGraphOperations()

        val vertices = (1..10).map { i ->
            src.createVertex("Person", mapOf("name" to "Person$i", "age" to (20 + i)))
        }

        (0 until 10).forEach { i ->
            val from = vertices[i]
            val to = vertices[(i + 1) % 10]
            src.createEdge(from.id, to.id, "KNOWS", mapOf("since" to "200${i}"))
        }

        val exporter = GraphMlBulkExporter()
        exporter.exportGraph(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = GraphMlBulkImporter()
        val report = importer.importGraph(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 10L
        report.edgesCreated shouldBeEqualTo 10L
        target.findVerticesByLabel("Person").size shouldBeEqualTo 10
    }
}

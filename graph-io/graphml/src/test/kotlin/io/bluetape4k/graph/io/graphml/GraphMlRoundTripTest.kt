package io.bluetape4k.graph.io.graphml

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

class GraphMlRoundTripTest {

    @Test
    fun `sync round trip three vertices two edges`(@TempDir dir: Path) {
        val out = dir.resolve("graph.graphml")

        val src = TinkerGraphOperations()
        val alice = src.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
        val bob = src.createVertex("Person", mapOf("name" to "Bob", "age" to 25))
        val charlie = src.createVertex("Person", mapOf("name" to "Charlie", "age" to 22))
        src.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2020"))
        src.createEdge(bob.id, charlie.id, "KNOWS", mapOf("since" to "2022"))

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
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `sync round trip with integer and double properties`(@TempDir dir: Path) {
        val out = dir.resolve("typed.graphml")

        val src = TinkerGraphOperations()
        val n1 = src.createVertex("Item", mapOf("price" to 9.99, "stock" to 100))
        val n2 = src.createVertex("Item", mapOf("price" to 4.5, "stock" to 50))
        src.createEdge(n1.id, n2.id, "RELATED", emptyMap())

        GraphMlBulkExporter().exportGraph(
            GraphExportSink.PathSink(out),
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
    }
}

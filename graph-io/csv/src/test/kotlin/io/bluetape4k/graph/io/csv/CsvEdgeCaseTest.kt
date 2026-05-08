package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CsvEdgeCaseTest {

    companion object : KLogging()

    @Test
    fun `export and import of empty graph completes successfully`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val source = TinkerGraphOperations()
        val exporter = CsvGraphBulkExporter()
        val exportReport = exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        )
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 0L
        exportReport.edgesWritten shouldBeEqualTo 0L

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val importReport = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(),
        )
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 0L
        importReport.edgesCreated shouldBeEqualTo 0L
    }

    @Test
    fun `export and import of vertices-only graph completes successfully`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val source = TinkerGraphOperations()
        source.createVertex("Person", mapOf("name" to "Alice"))
        source.createVertex("Person", mapOf("name" to "Bob"))

        val exporter = CsvGraphBulkExporter()
        val exportReport = exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        )
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 0L

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val importReport = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(),
        )
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 0L
    }

    @Test
    fun `import with None property mode excludes prefixed properties from round-trip`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val source = TinkerGraphOperations()
        val alice = source.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
        val bob = source.createVertex("Person", mapOf("name" to "Bob", "age" to 25))
        source.createEdge(alice.id, bob.id, "KNOWS", emptyMap())

        // Export with default PrefixedColumns to produce prop.name, prop.age columns
        val defaultOptions = CsvGraphIoOptions()
        val exporter = CsvGraphBulkExporter()
        val exportReport = exporter.exportGraph(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
            defaultOptions,
        )
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L

        // Import with None mode — prefixed prop.* columns are ignored
        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val importReport = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(),
            CsvGraphIoOptions(propertyMode = CsvPropertyMode.None),
        )
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L

        val createdVertices = target.findVerticesByLabel("Person")
        // None mode extractProperties returns emptyMap — no properties preserved
        createdVertices.all { it.properties["name"] == null && it.properties["age"] == null } shouldBeEqualTo true
    }

    @Test
    fun `import preserves external id as property when preserveExternalIdProperty is set`(@TempDir dir: Path) {
        val vCsv = "id,label,prop.name\nv-alice,Person,Alice\n"
        val eCsv = "id,label,from,to\n"
        val vFile = dir.resolve("v.csv").also { it.toFile().writeText(vCsv) }
        val eFile = dir.resolve("e.csv").also { it.toFile().writeText(eCsv) }

        val target = TinkerGraphOperations()
        val importer = CsvGraphBulkImporter()
        val report = importer.importGraph(
            CsvGraphImportSource(GraphImportSource.PathSource(vFile), GraphImportSource.PathSource(eFile)),
            target,
            GraphImportOptions(preserveExternalIdProperty = "_extId"),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 1L

        val vertices = target.findVerticesByLabel("Person")
        vertices.all { it.properties["_extId"] == "v-alice" } shouldBeEqualTo true
    }
}

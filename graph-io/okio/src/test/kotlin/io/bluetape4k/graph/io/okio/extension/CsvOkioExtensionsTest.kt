package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * CSV OkIO extension function tests.
 *
 * Uses @TempDir (real filesystem) because CSV requires FileSystem.SYSTEM.
 */
class CsvOkioExtensionsTest {

    @TempDir
    lateinit var tempDir: Path

    private val exporter = CsvGraphBulkExporter()
    private val importer = CsvGraphBulkImporter()

    private fun buildSourceGraph(): TinkerGraphOperations {
        val ops = TinkerGraphOperations()
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS", emptyMap())
        return ops
    }

    private val exportOptions = GraphExportOptions(
        vertexLabels = setOf("Person"),
        edgeLabels = setOf("KNOWS"),
    )

    private fun csvSink(): OkioGraphExportSink.PathSink =
        OkioGraphExportSink.PathSink(
            tempDir.resolve("graph.csv").toString().toPath(),
            FileSystem.SYSTEM,
        )

    private fun csvSource(): OkioGraphImportSource.PathSource =
        OkioGraphImportSource.PathSource(
            tempDir.resolve("graph.csv").toString().toPath(),
            FileSystem.SYSTEM,
        )

    @Test
    fun `CSV sync round trip`() {
        exporter.exportGraph(csvSink(), buildSourceGraph(), exportOptions)
            .status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = importer.importGraph(csvSource(), TinkerGraphOperations(), GraphImportOptions())
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `CSV gzip round trip`() {
        val gzSink = OkioGraphExportSink.PathSink(
            tempDir.resolve("graph.csv.gz").toString().toPath(),
            FileSystem.SYSTEM,
        )
        exporter.exportGraphGzip(gzSink, buildSourceGraph(), exportOptions)
            .status shouldBeEqualTo GraphIoStatus.COMPLETED

        val gzSource = OkioGraphImportSource.PathSource(
            tempDir.resolve("graph.csv.gz").toString().toPath(),
            FileSystem.SYSTEM,
        )
        val report = importer.importGraphGzip(gzSource, TinkerGraphOperations(), GraphImportOptions())
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `CSV exportGraphAsync completes via VirtualThread`() {
        val future = exporter.exportGraphAsync(csvSink(), buildSourceGraph(), exportOptions)
        future.get().status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `CSV importGraphAsync completes via VirtualThread`() {
        exporter.exportGraph(csvSink(), buildSourceGraph(), exportOptions)
        val future = importer.importGraphAsync(csvSource(), TinkerGraphOperations(), GraphImportOptions())
        val report = future.get()
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    // ─── Suspend ─────────────────────────────────────────────────────────────

    @Test
    fun `CSV exportGraphAwait completes via coroutine`() = runTest {
        val report = exporter.exportGraphAwait(csvSink(), buildSourceGraph(), exportOptions)
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `CSV importGraphAwait completes via coroutine`() = runTest {
        exporter.exportGraph(csvSink(), buildSourceGraph(), exportOptions)
        val report = importer.importGraphAwait(csvSource(), TinkerGraphOperations(), GraphImportOptions())
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `CSV exportGraphFlow emits progress`() = runTest {
        val events = exporter.exportGraphFlow(csvSink(), buildSourceGraph(), exportOptions).toList()
        events shouldHaveSize 2
    }

    @Test
    fun `CSV importGraphFlow emits progress`() = runTest {
        exporter.exportGraph(csvSink(), buildSourceGraph(), exportOptions)
        val events = importer.importGraphFlow(csvSource(), TinkerGraphOperations(), GraphImportOptions()).toList()
        events shouldHaveSize 2
    }
}

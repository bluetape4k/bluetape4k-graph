package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.concurrent.get
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.okio.AbstractOkioTest
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.okio.asOkioSink
import io.bluetape4k.graph.io.okio.asOkioSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * CSV OkIO extension function tests.
 *
 * Uses @TempDir (real filesystem) because CSV requires FileSystem.SYSTEM.
 */
class CsvOkioExtensionsTest: AbstractOkioTest() {

    companion object: KLogging()

    @TempDir
    lateinit var tempDir: Path

    private val exporter = CsvGraphBulkExporter.Default
    private val importer = CsvGraphBulkImporter.Default

    private fun csvSink(): OkioGraphExportSink.PathSink =
        tempDir.resolve("graph.csv").toString().toPath().asOkioSink(FileSystem.SYSTEM)

    private fun csvSource(): OkioGraphImportSource.PathSource =
        tempDir.resolve("graph.csv").toString().toPath().asOkioSource(FileSystem.SYSTEM)

    @Test
    fun `CSV sync round trip`() {
        val exportReport = exporter.exportGraph(
            csvSink(),
            buildSourceGraph(),
            exportOptions
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val importReport = importer.importGraph(
            csvSource(),
            TinkerGraphOperations(),
            GraphImportOptions()
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `CSV gzip round trip`() {
        val path = tempDir.resolve("graph.csv.gz").toString().toPath()
        val gzSink = path.asOkioSink(FileSystem.SYSTEM)

        val exportReport = exporter.exportGraphGzip(
            gzSink,
            buildSourceGraph(),
            exportOptions
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val gzSource = path.asOkioSource(FileSystem.SYSTEM)

        val importSource = importer.importGraphGzip(
            gzSource,
            TinkerGraphOperations(),
            GraphImportOptions.Default
        )
        log.debug { "import source=$importSource" }
        importSource.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importSource.verticesCreated shouldBeEqualTo 2L
        importSource.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `CSV exportGraphAsync completes via VirtualThread`() {
        val future = exporter.exportGraphAsync(
            csvSink(),
            buildSourceGraph(),
            exportOptions
        )

        val exportReport = future.get(3.seconds)
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L
    }

    @Test
    fun `CSV importGraphAsync completes via VirtualThread`() {
        exporter.exportGraph(
            csvSink(),
            buildSourceGraph(),
            exportOptions
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED


        val importFuture = importer.importGraphAsync(
            csvSource(),
            TinkerGraphOperations(),
            GraphImportOptions()
        )

        val importReport = importFuture.get(3.seconds)
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    // ─── Suspend ─────────────────────────────────────────────────────────────

    @Test
    fun `CSV exportGraphAwait completes via coroutine`() = runSuspendIO {
        val exportReport = exporter.exportGraphAwait(
            csvSink(),
            buildSourceGraph(),
            exportOptions
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L
    }

    @Test
    fun `CSV importGraphAwait completes via coroutine`() = runSuspendIO {
        exporter.exportGraph(
            csvSink(),
            buildSourceGraph(),
            exportOptions
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val importReport = importer.importGraphAwait(
            csvSource(),
            TinkerGraphOperations(),
            GraphImportOptions()
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `CSV exportGraphFlow emits progress`() = runSuspendIO {
        val events = exporter
            .exportGraphFlow(
                csvSink(),
                buildSourceGraph(),
                exportOptions
            )
            .log("Export")
            .toList()

        events shouldHaveSize 2
    }

    @Test
    fun `CSV importGraphFlow emits progress`() = runSuspendIO {
        exporter.exportGraph(
            csvSink(),
            buildSourceGraph(),
            exportOptions
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val events = importer
            .importGraphFlow(
                csvSource(),
                TinkerGraphOperations(),
                GraphImportOptions()
            )
            .log("Import")
            .toList()

        events shouldHaveSize 2
    }
}

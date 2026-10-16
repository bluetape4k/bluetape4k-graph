package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.okio.AbstractOkioTest
import io.bluetape4k.graph.io.okio.asOkioSink
import io.bluetape4k.graph.io.okio.asOkioSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class GraphMLOkioExtensionsTest: AbstractOkioTest() {

    companion object: KLogging()

    @Test
    fun `GraphML sync round trip`() {
        val path = "/graph.graphml".toPath()
        val src = buildSourceGraph()

        val exportReport = GraphMlBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                src,
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = GraphMlBulkImporter.Default
            .importGraph(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()

    }

    @Test
    fun `GraphML gzip round trip`() {
        val path = "/graph.graphml.gz".toPath()
        val src = buildSourceGraph()

        val exportReport = GraphMlBulkExporter.Default
            .exportGraphGzip(
                path.asOkioSink(fakeFs),
                src,
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = GraphMlBulkImporter.Default
            .importGraphGzip(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    // ─── VirtualThread ────────────────────────────────────────────────────────

    @Test
    fun `GraphML exportGraphAsync returns completed report`() {
        val path = "/graph-vt.graphml".toPath()
        val exportReport = GraphMlBulkExporter()
            .exportGraphAsync(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
            .get()

        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()
    }

    @Test
    fun `GraphML importGraphAsync returns completed report`() {
        val path = "/graph-vt-import.graphml".toPath()
        val exportReport = GraphMlBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        exportReport.assertSuccessful()

        val importReport = GraphMlBulkImporter()
            .importGraphAsync(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
            .get()

        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    // ─── Suspend ─────────────────────────────────────────────────────────────

    @Test
    fun `GraphML exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/graph-await.graphml".toPath()
        val exportReport = GraphMlBulkExporter.Default
            .exportGraphAwait(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()
    }

    @Test
    fun `GraphML importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/graph-await-import.graphml".toPath()
        val exportReport = GraphMlBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = GraphMlBulkImporter.Default
            .importGraphAwait(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    @Test
    fun `GraphML exportGraphFlow emits progress`() = runSuspendIO {
        val path = "/graph-flow-export.graphml".toPath()
        val events = GraphMlBulkExporter()
            .exportGraphFlow(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
            .log("Export")
            .toList()

        events shouldHaveSize 2
    }

    @Test
    fun `GraphML importGraphFlow emits progress`() = runSuspendIO {
        val path = "/graph-flow-import.graphml".toPath()
        val exportReport = GraphMlBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val events = GraphMlBulkImporter.Default
            .importGraphFlow(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
            .log("Import")
            .toList()
        events shouldHaveSize 2
    }
}

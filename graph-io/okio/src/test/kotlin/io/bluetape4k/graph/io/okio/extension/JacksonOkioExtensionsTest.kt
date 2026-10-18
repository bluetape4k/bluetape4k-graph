package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.concurrent.get
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.okio.AbstractOkioTest
import io.bluetape4k.graph.io.okio.asOkioSink
import io.bluetape4k.graph.io.okio.asOkioSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class JacksonOkioExtensionsTest: AbstractOkioTest() {

    @Test
    fun `Jackson Okio extensions keep the legacy JVM facade`() {
        val facade = Class.forName("io.bluetape4k.graph.io.okio.extension.JacksonOkioExtensionsKt")

        facade.declaredMethods.any { it.name == "importGraph" }.shouldBeTrue()
        facade.declaredMethods.any { it.name == "exportGraph" }.shouldBeTrue()

        val overloadCounts = facade.declaredMethods
            .filterNot { it.isSynthetic }
            .groupingBy { it.name }
            .eachCount()
        overloadCounts shouldBeEqualTo mapOf(
            "importGraph" to 4,
            "importGraphGzip" to 4,
            "exportGraph" to 4,
            "exportGraphGzip" to 4,
            "importGraphAsync" to 4,
            "exportGraphAsync" to 4,
            "importGraphFlow" to 4,
            "importGraphAwait" to 4,
            "exportGraphFlow" to 4,
            "exportGraphAwait" to 4,
        )
    }

    // ─── Jackson 2 ────────────────────────────────────────────────────────────

    @Test
    fun `Jackson2 sync round trip`() {
        val path = "/j2.ndjson".toPath()
        val src = buildSourceGraph()
        val events = mutableListOf<GraphIoProgressEventType>()

        val exportReport = Jackson2NdJsonBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                src, exportOptions,
                listener = { events += it.type },
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        events.first() shouldBeEqualTo GraphIoProgressEventType.STARTED
        events.last() shouldBeEqualTo GraphIoProgressEventType.COMPLETED

        val importReport = Jackson2NdJsonBulkImporter.Default
            .importGraph(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    @Test
    fun `Jackson2 gzip round trip`() {
        val path = "/j2.ndjson.gz".toPath()
        val src = buildSourceGraph()

        val exportReport = Jackson2NdJsonBulkExporter()
            .exportGraphGzip(
                path.asOkioSink(fakeFs),
                src,
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = Jackson2NdJsonBulkImporter.Default
            .importGraphGzip(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    // ─── Jackson 3 ────────────────────────────────────────────────────────────

    @Test
    fun `Jackson3 sync round trip`() {
        val path = "/j3.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = Jackson3NdJsonBulkExporter.Default.exportGraph(
            path.asOkioSink(fakeFs),
            src,
            exportOptions,
        )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = Jackson3NdJsonBulkImporter.Default
            .importGraph(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    @Test
    fun `Jackson3 gzip round trip`() {
        val path = "/j3.ndjson.gz".toPath()
        val src = buildSourceGraph()

        val exportReport = Jackson3NdJsonBulkExporter.Default
            .exportGraphGzip(
                path.asOkioSink(fakeFs),
                src,
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = Jackson3NdJsonBulkImporter.Default
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
    fun `Jackson3 exportGraphAsync returns completed report`() {
        val path = "/j3-vt.ndjson".toPath()
        val exportReport = Jackson3NdJsonBulkExporter.Default
            .exportGraphAsync(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            ).get(3.seconds)

        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()
    }

    @Test
    fun `Jackson3 importGraphAsync returns completed report`() {
        val path = "/j3-vt-import.ndjson".toPath()
        val exportReport = Jackson3NdJsonBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = Jackson3NdJsonBulkImporter.Default
            .importGraphAsync(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
            .get(3.seconds)

        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    @Test
    fun `Jackson2 exportGraphAsync returns completed report`() {
        val path = "/j2-vt.ndjson".toPath()
        val exportReport = Jackson2NdJsonBulkExporter.Default
            .exportGraphAsync(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
            .get(3.seconds)

        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()
    }

    // ─── Suspend ─────────────────────────────────────────────────────────────

    @Test
    fun `Jackson3 exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j3-await.ndjson".toPath()
        val exportReport = Jackson3NdJsonBulkExporter.Default
            .exportGraphAwait(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report= $exportReport" }
        exportReport.assertSuccessful()
    }

    @Test
    fun `Jackson3 importGraphFlow emits progress`() = runSuspendIO {
        val path = "/j3-flow.ndjson".toPath()
        val exportReport = Jackson3NdJsonBulkExporter.Default.exportGraph(
            path.asOkioSink(fakeFs),
            buildSourceGraph(),
            exportOptions,
        )
        exportReport.assertSuccessful()

        val events = Jackson3NdJsonBulkImporter.Default
            .importGraphFlow(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
            .log("Event")
            .toList()

        events shouldHaveSize 2
    }

    @Test
    fun `Jackson3 importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j3-await-import.ndjson".toPath()
        val exportReport = Jackson3NdJsonBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()

        val importReport = Jackson3NdJsonBulkImporter.Default
            .importGraphAwait(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()
    }

    // ─── Jackson 2 Suspend / Flow ─────────────────────────────────────────────

    @Test
    fun `Jackson2 exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j2-await.ndjson".toPath()
        val exportReport = Jackson2NdJsonBulkExporter.Default
            .exportGraphAwait(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        log.debug { "export report=$exportReport" }
        exportReport.assertSuccessful()
    }

    @Test
    fun `Jackson2 importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j2-await-import.ndjson".toPath()
        val exportReport = Jackson2NdJsonBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        exportReport.assertSuccessful()

        val importReport = Jackson2NdJsonBulkImporter.Default
            .importGraphAwait(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
        importReport.assertSuccessful()
    }

    @Test
    fun `Jackson2 exportGraphFlow emits progress`() = runSuspendIO {
        val path = "/j2-flow-export.ndjson".toPath()

        val events = Jackson2NdJsonBulkExporter.Default
            .exportGraphFlow(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
            .log("Event")
            .toList()

        events shouldHaveSize 2
    }

    @Test
    fun `Jackson2 importGraphFlow emits progress`() = runSuspendIO {
        val path = "/j2-flow-import.ndjson".toPath()
        val exportReport = Jackson2NdJsonBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        exportReport.assertSuccessful()

        val events = Jackson2NdJsonBulkImporter.Default
            .importGraphFlow(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
            .log("Event")
            .toList()

        events shouldHaveSize 2
    }

    @Test
    fun `Jackson2 importGraphAsync returns completed report`() {
        val path = "/j2-vt-import.ndjson".toPath()

        val exportReport = Jackson2NdJsonBulkExporter.Default
            .exportGraph(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
        exportReport.assertSuccessful()

        val importReport = Jackson2NdJsonBulkImporter.Default
            .importGraphAsync(
                path.asOkioSource(fakeFs),
                TinkerGraphOperations(),
                GraphImportOptions(),
            )
            .get(3.seconds)
        importReport.assertSuccessful()
    }

    @Test
    fun `Jackson3 exportGraphFlow emits progress`() = runSuspendIO {
        val path = "/j3-flow-export.ndjson".toPath()
        val events = Jackson3NdJsonBulkExporter.Default
            .exportGraphFlow(
                path.asOkioSink(fakeFs),
                buildSourceGraph(),
                exportOptions,
            )
            .log("Event")
            .toList()
        
        events shouldHaveSize 2
    }
}

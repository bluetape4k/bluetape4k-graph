package io.bluetape4k.graph.io.okio.coroutines

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.okio.AbstractOkioTest
import io.bluetape4k.graph.io.okio.asOkioSink
import io.bluetape4k.graph.io.okio.asOkioSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class SuspendAdapterTest: AbstractOkioTest() {

    companion object: KLogging()

    private val adapter = SuspendGraphIoOkioBulkAdapter.Default

    @Test
    fun `importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/await.ndjson".toPath()
        val src = buildSourceGraph()
        val sink = path.asOkioSink(fakeFs)

        val exportReport = adapter.exportGraphAwait(
            sink,
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val importReport = adapter.importGraphAwait(
            path.asOkioSource(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            TinkerGraphOperations(),
            GraphImportOptions(),
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/await-export.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = adapter.exportGraphAwait(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions,
        )

        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L
    }

    @Test
    fun `importGraph Flow emits start and completion progress`() = runSuspendIO {
        val path = "/flow.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = adapter.exportGraphAwait(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src, exportOptions,
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val events = adapter.importGraph(
            path.asOkioSource(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            TinkerGraphOperations(),
            GraphImportOptions(),
        )
            .log("IMPORT")
            .toList()

        events shouldHaveSize 2
        events.first().processed shouldBeEqualTo 0L
        events.last().processed shouldBeEqualTo 3L  // 2 vertices + 1 edge
    }

    @Test
    fun `exportGraph Flow emits start and completion progress`() = runSuspendIO {
        val path = "/flow-export.ndjson".toPath()
        val src = buildSourceGraph()

        val events = adapter.exportGraph(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src, exportOptions,
        )
            .log("EXPORT")
            .toList()

        events shouldHaveSize 2
        events.first().exported shouldBeEqualTo 0L
        events.last().exported shouldBeEqualTo 3L  // 2 vertices + 1 edge
    }

    // ─── 다중 포맷 커버리지 ───────────────────────────────────────────────────────

    @Test
    fun `importGraphAwait with NDJSON_JACKSON2 format returns correct counts`() = runSuspendIO {
        val path = "/await-j2.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = adapter.exportGraphAwait(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            src,
            exportOptions
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val importReport = adapter.importGraphAwait(
            path.asOkioSource(fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            TinkerGraphOperations(),
            GraphImportOptions(),
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `importGraphAwait with GRAPHML format returns correct counts`() = runSuspendIO {
        val path = "/await-graphml.xml".toPath()
        val src = buildSourceGraph()

        val exportReport = adapter.exportGraphAwait(
            path.asOkioSink(fakeFs),
            GraphIoFormat.GRAPHML,
            src,
            exportOptions
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val importReport = adapter.importGraphAwait(
            path.asOkioSource(fakeFs),
            GraphIoFormat.GRAPHML,
            TinkerGraphOperations(),
            GraphImportOptions(),
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `exportGraphAwait with GRAPHML format returns completed report`() = runSuspendIO {
        val path = "/await-export-graphml.xml".toPath()
        val src = buildSourceGraph()

        val exportReport = adapter.exportGraphAwait(
            path.asOkioSink(fakeFs),
            GraphIoFormat.GRAPHML,
            src,
            exportOptions,
        )

        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L
    }

    // ─── 취소 전파 ────────────────────────────────────────────────────────────

    @Test
    fun `exportGraphAwait throws CancellationException when coroutine is pre-cancelled`() = runSuspendIO {
        val cancelledJob = Job().apply { cancel() }

        assertFailsWith<CancellationException> {
            withContext(cancelledJob) {
                adapter.exportGraphAwait(
                    "/nope-export.ndjson".toPath().asOkioSink(fakeFs),
                    GraphIoFormat.NDJSON_JACKSON3,
                    buildSourceGraph(),
                    exportOptions,
                )
            }
        }
        // fakeFs.checkNoOpenFiles() in @AfterEach verifies no resource leak
    }

    @Test
    fun `importGraphAwait throws CancellationException when coroutine is pre-cancelled`() = runSuspendIO {
        val path = "/cancel-import.ndjson".toPath()

        val exportReport = adapter.exportGraphAwait(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            buildSourceGraph(),
            exportOptions,
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val cancelledJob = Job().apply { cancel() }

        assertFailsWith<CancellationException> {
            withContext(cancelledJob) {
                adapter.importGraphAwait(
                    path.asOkioSource(fakeFs),
                    GraphIoFormat.NDJSON_JACKSON3,
                    TinkerGraphOperations(),
                    GraphImportOptions(),
                )
            }
        }
    }
}

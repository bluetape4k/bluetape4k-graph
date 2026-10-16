package io.bluetape4k.graph.io.okio.virtualthread

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.concurrent.sequence
import io.bluetape4k.graph.io.okio.AbstractOkioTest
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.okio.asOkioSink
import io.bluetape4k.graph.io.okio.asOkioSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadGraphIoOkioBulkAdapterTest: AbstractOkioTest() {

    companion object: KLogging()

    private val adapter = VirtualThreadGraphIoOkioBulkAdapter.Default


    @Test
    fun `importGraphAsync returns completed report`() {
        val path = "/vt-import.ndjson".toPath()
        val src = buildSourceGraph()
        val sink = path.asOkioSink(fakeFs)

        val exportReport = adapter.exportGraphAsync(
            sink,
            GraphIoFormat.NDJSON_JACKSON3,
            src, exportOptions
        ).get()
        exportReport.assertSuccessful()

        val importReport = adapter.importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        log.debug { "import report=$importReport" }
        importReport.assertSuccessful()

    }

    @Test
    fun `exportGraphAsync returns completed report`() {
        val path = "/vt-export.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = adapter.exportGraphAsync(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions,
        ).get()

        exportReport.assertSuccessful()
    }

    @Test
    fun `listener overload emits one ordered lifecycle`() {
        val path = "/vt-listener.ndjson".toPath()
        val events = mutableListOf<GraphIoProgressEventType>()

        val exportReport = adapter
            .exportGraphAsync(
                path.asOkioSink(fakeFs),
                GraphIoFormat.NDJSON_JACKSON3,
                buildSourceGraph(),
                exportOptions,
            ) { events += it.type }.get()

        exportReport.assertSuccessful()
        events shouldBeEqualTo listOf(
            GraphIoProgressEventType.STARTED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PHASE_COMPLETED,
            GraphIoProgressEventType.PROGRESS,
            GraphIoProgressEventType.COMPLETED,
        )
    }

    @Test
    fun `concurrent exports do not interfere`() {
        // FakeFileSystem is not thread-safe: each concurrent operation uses its own instance

        val futures = List(10) { i ->
            val fs = FakeFileSystem()
            val path = "/concurrent-$i.ndjson".toPath()

            adapter
                .exportGraphAsync(
                    path.asOkioSink(fs),
                    GraphIoFormat.NDJSON_JACKSON3,
                    buildSourceGraph(),
                    exportOptions,
                )
        }

        val reports = futures.sequence().get()

        reports.forEach { report ->
            report.assertSuccessful()
        }
    }

    @Test
    fun `concurrent round trips produce correct results`() {
        // FakeFileSystem is not thread-safe: each concurrent round trip uses its own instance
        val futures = List(5) { i ->
            val fs = FakeFileSystem()
            val exportPath = "/rt-$i.ndjson".toPath()

            adapter.exportGraphAsync(
                exportPath.asOkioSink(fs),
                GraphIoFormat.NDJSON_JACKSON3,
                buildSourceGraph(),
                exportOptions,
            ).thenCompose { _ ->
                adapter.importGraphAsync(
                    exportPath.asOkioSource(fs),
                    GraphIoFormat.NDJSON_JACKSON3,
                    TinkerGraphOperations(),
                    GraphImportOptions(),
                )
            }
        }
        futures.sequence().join()

        futures.forEach { future ->
            val report = future.get()
            report.assertSuccessful()
        }
    }

    @Test
    fun `graphml round trip via virtual thread`() {
        val path = "/vt-graph.graphml".toPath()
        val src = buildSourceGraph()

        adapter.exportGraphAsync(
            path.asOkioSink(fakeFs),
            GraphIoFormat.GRAPHML,
            src,
            exportOptions,
        ).get()

        val report = adapter.importGraphAsync(
            path.asOkioSource(fakeFs),
            GraphIoFormat.GRAPHML,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        report.assertSuccessful()
    }

    @Test
    fun `jackson2 round trip via virtual thread`() {
        val path = "/vt-graph-j2.ndjson".toPath()
        val src = buildSourceGraph()

        adapter.exportGraphAsync(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            src,
            exportOptions,
        ).get()

        val report = adapter.importGraphAsync(
            path.asOkioSource(fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        report.assertSuccessful()
    }

    @Test
    fun `export duration is positive`() {
        val path = "/vt-dur.ndjson".toPath()
        val report = adapter.exportGraphAsync(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            buildSourceGraph(),
            exportOptions,
        ).get()

        report.assertSuccessful()
        report.elapsed.toNanos() shouldBeGreaterThan 0L
    }
}

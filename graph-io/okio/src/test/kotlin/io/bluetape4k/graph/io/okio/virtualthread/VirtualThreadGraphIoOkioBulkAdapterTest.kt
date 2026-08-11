package io.bluetape4k.graph.io.okio.virtualthread

import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CompletableFuture

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadGraphIoOkioBulkAdapterTest {

    private val fakeFs = FakeFileSystem()
    private val adapter = VirtualThreadGraphIoOkioBulkAdapter(
        importer = OkioGraphBulkImporter(),
        exporter = OkioGraphBulkExporter(),
    )

    @AfterEach
    fun cleanup() {
        fakeFs.checkNoOpenFiles()
    }

    @AfterAll
    fun teardown() {
        fakeFs.close()
    }

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

    @Test
    fun `importGraphAsync returns completed report`() {
        val path = "/vt-import.ndjson".toPath()
        val src = buildSourceGraph()
        val sink = OkioGraphExportSink.PathSink(path, fakeFs)
        adapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON3, src, exportOptions).get()

        val report = adapter.importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `exportGraphAsync returns completed report`() {
        val path = "/vt-export.ndjson".toPath()
        val src = buildSourceGraph()

        val report = adapter.exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions,
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
        report.edgesWritten shouldBeEqualTo 1L
    }

    @Test
    fun `listener overload emits one ordered lifecycle`() {
        val path = "/vt-listener.ndjson".toPath()
        val events = mutableListOf<GraphIoProgressEventType>()

        val report = adapter.exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            buildSourceGraph(),
            exportOptions,
            GraphIoProgressListener { events += it.type },
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
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
        val futures = (0 until 10).map { i ->
            val fs = FakeFileSystem()
            val path = "/concurrent-$i.ndjson".toPath()
            adapter.exportGraphAsync(
                OkioGraphExportSink.PathSink(path, fs),
                GraphIoFormat.NDJSON_JACKSON3,
                buildSourceGraph(),
                exportOptions,
            )
        }
        val reports = futures.map { it.get() }
        reports.forEach { report ->
            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesWritten shouldBeEqualTo 2L
        }
    }

    @Test
    fun `concurrent round trips produce correct results`() {
        // FakeFileSystem is not thread-safe: each concurrent round trip uses its own instance
        val futures = (0 until 5).map { i ->
            val fs = FakeFileSystem()
            val exportPath = "/rt-$i.ndjson".toPath()
            adapter.exportGraphAsync(
                OkioGraphExportSink.PathSink(exportPath, fs),
                GraphIoFormat.NDJSON_JACKSON3,
                buildSourceGraph(),
                exportOptions,
            ).thenCompose { _ ->
                adapter.importGraphAsync(
                    OkioGraphImportSource.PathSource(exportPath, fs),
                    GraphIoFormat.NDJSON_JACKSON3,
                    TinkerGraphOperations(),
                    GraphImportOptions(),
                )
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).get()
        futures.forEach { future ->
            val report = future.get()
            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesCreated shouldBeEqualTo 2L
            report.edgesCreated shouldBeEqualTo 1L
        }
    }

    @Test
    fun `graphml round trip via virtual thread`() {
        val path = "/vt-graph.graphml".toPath()
        val src = buildSourceGraph()

        adapter.exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.GRAPHML,
            src,
            exportOptions,
        ).get()

        val report = adapter.importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.GRAPHML,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `jackson2 round trip via virtual thread`() {
        val path = "/vt-graph-j2.ndjson".toPath()
        val src = buildSourceGraph()

        adapter.exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            src,
            exportOptions,
        ).get()

        val report = adapter.importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `export duration is positive`() {
        val path = "/vt-dur.ndjson".toPath()
        val report = adapter.exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            buildSourceGraph(),
            exportOptions,
        ).get()

        report.elapsed.toNanos() shouldBeGreaterThan 0L
    }
}

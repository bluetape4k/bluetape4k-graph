package io.bluetape4k.graph.io.okio.virtualthread

import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
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
    fun `concurrent exports do not interfere`() {
        val futures = (0 until 10).map { i ->
            val path = "/concurrent-$i.ndjson".toPath()
            adapter.exportGraphAsync(
                OkioGraphExportSink.PathSink(path, fakeFs),
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
        val futures = (0 until 5).map { i ->
            val exportPath = "/rt-$i.ndjson".toPath()
            adapter.exportGraphAsync(
                OkioGraphExportSink.PathSink(exportPath, fakeFs),
                GraphIoFormat.NDJSON_JACKSON3,
                buildSourceGraph(),
                exportOptions,
            ).thenCompose { _ ->
                adapter.importGraphAsync(
                    OkioGraphImportSource.PathSource(exportPath, fakeFs),
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
    fun `export duration is positive`() {
        val path = "/vt-dur.ndjson".toPath()
        val report = adapter.exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            buildSourceGraph(),
            exportOptions,
        ).get()

        report.elapsed.toMillis() shouldBeGreaterThan 0L
    }
}

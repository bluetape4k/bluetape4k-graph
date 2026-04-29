package io.bluetape4k.graph.io.okio.coroutines

import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class SuspendAdapterTest {

    private val fakeFs = FakeFileSystem()
    private val adapter = SuspendGraphIoOkioBulkAdapter(
        importer = OkioGraphBulkImporter(),
        exporter = OkioGraphBulkExporter(),
    )

    @AfterEach
    fun cleanup() {
        fakeFs.checkNoOpenFiles()
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
    fun `importGraphAwait returns completed report`() = runTest {
        val path = "/await.ndjson".toPath()
        val src = buildSourceGraph()
        val sink = OkioGraphExportSink.PathSink(path, fakeFs)
        adapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON3, src, exportOptions)

        val report = adapter.importGraphAwait(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            TinkerGraphOperations(),
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `exportGraphAwait returns completed report`() = runTest {
        val path = "/await-export.ndjson".toPath()
        val src = buildSourceGraph()

        val report = adapter.exportGraphAwait(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions,
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
        report.edgesWritten shouldBeEqualTo 1L
    }

    @Test
    fun `importGraph Flow emits start and completion progress`() = runTest {
        val path = "/flow.ndjson".toPath()
        val src = buildSourceGraph()
        adapter.exportGraphAwait(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src, exportOptions,
        )

        val events = adapter.importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).toList()

        events shouldHaveSize 2
        events.first().processed shouldBeEqualTo 0L
        events.last().processed shouldBeEqualTo 3L  // 2 vertices + 1 edge
    }

    @Test
    fun `exportGraph Flow emits start and completion progress`() = runTest {
        val path = "/flow-export.ndjson".toPath()
        val src = buildSourceGraph()

        val events = adapter.exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src, exportOptions,
        ).toList()

        events shouldHaveSize 2
        events.first().exported shouldBeEqualTo 0L
        events.last().exported shouldBeEqualTo 3L  // 2 vertices + 1 edge
    }
}

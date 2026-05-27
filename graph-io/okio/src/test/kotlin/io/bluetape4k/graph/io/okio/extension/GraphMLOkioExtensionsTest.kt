package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class GraphMLOkioExtensionsTest {

    private val fakeFs = FakeFileSystem()

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
    fun `GraphML sync round trip`() {
        val path = "/graph.graphml".toPath()
        val src = buildSourceGraph()

        GraphMlBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            src, exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = GraphMlBulkImporter().importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `GraphML gzip round trip`() {
        val path = "/graph.graphml.gz".toPath()
        val src = buildSourceGraph()

        GraphMlBulkExporter().exportGraphGzip(
            OkioGraphExportSink.PathSink(path, fakeFs),
            src, exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = GraphMlBulkImporter().importGraphGzip(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    // ─── VirtualThread ────────────────────────────────────────────────────────

    @Test
    fun `GraphML exportGraphAsync returns completed report`() {
        val path = "/graph-vt.graphml".toPath()
        val report = GraphMlBulkExporter().exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        ).get()
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
    }

    @Test
    fun `GraphML importGraphAsync returns completed report`() {
        val path = "/graph-vt-import.graphml".toPath()
        GraphMlBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val report = GraphMlBulkImporter().importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        ).get()
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    // ─── Suspend ─────────────────────────────────────────────────────────────

    @Test
    fun `GraphML exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/graph-await.graphml".toPath()
        val report = GraphMlBulkExporter().exportGraphAwait(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
    }

    @Test
    fun `GraphML importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/graph-await-import.graphml".toPath()
        GraphMlBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val report = GraphMlBulkImporter().importGraphAwait(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `GraphML exportGraphFlow emits progress`() = runSuspendIO {
        val path = "/graph-flow-export.graphml".toPath()
        val events = GraphMlBulkExporter().exportGraphFlow(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        ).toList()
        events shouldHaveSize 2
    }

    @Test
    fun `GraphML importGraphFlow emits progress`() = runSuspendIO {
        val path = "/graph-flow-import.graphml".toPath()
        GraphMlBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val events = GraphMlBulkImporter().importGraphFlow(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        ).toList()
        events shouldHaveSize 2
    }
}

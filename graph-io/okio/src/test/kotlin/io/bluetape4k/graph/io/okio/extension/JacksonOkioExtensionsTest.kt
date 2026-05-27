package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
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

class JacksonOkioExtensionsTest {

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

    // ─── Jackson 2 ────────────────────────────────────────────────────────────

    @Test
    fun `Jackson2 sync round trip`() {
        val path = "/j2.ndjson".toPath()
        val src = buildSourceGraph()

        Jackson2NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            src, exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = Jackson2NdJsonBulkImporter().importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `Jackson2 gzip round trip`() {
        val path = "/j2.ndjson.gz".toPath()
        val src = buildSourceGraph()

        Jackson2NdJsonBulkExporter().exportGraphGzip(
            OkioGraphExportSink.PathSink(path, fakeFs),
            src, exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = Jackson2NdJsonBulkImporter().importGraphGzip(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    // ─── Jackson 3 ────────────────────────────────────────────────────────────

    @Test
    fun `Jackson3 sync round trip`() {
        val path = "/j3.ndjson".toPath()
        val src = buildSourceGraph()

        Jackson3NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            src, exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = Jackson3NdJsonBulkImporter().importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `Jackson3 gzip round trip`() {
        val path = "/j3.ndjson.gz".toPath()
        val src = buildSourceGraph()

        Jackson3NdJsonBulkExporter().exportGraphGzip(
            OkioGraphExportSink.PathSink(path, fakeFs),
            src, exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = Jackson3NdJsonBulkImporter().importGraphGzip(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    // ─── VirtualThread ────────────────────────────────────────────────────────

    @Test
    fun `Jackson3 exportGraphAsync returns completed report`() {
        val path = "/j3-vt.ndjson".toPath()
        val report = Jackson3NdJsonBulkExporter().exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            buildSourceGraph(), exportOptions,
        ).get()
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
    }

    @Test
    fun `Jackson3 importGraphAsync returns completed report`() {
        val path = "/j3-vt-import.ndjson".toPath()
        Jackson3NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val report = Jackson3NdJsonBulkImporter().importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        ).get()
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `Jackson2 exportGraphAsync returns completed report`() {
        val path = "/j2-vt.ndjson".toPath()
        val report = Jackson2NdJsonBulkExporter().exportGraphAsync(
            OkioGraphExportSink.PathSink(path, fakeFs),
            buildSourceGraph(), exportOptions,
        ).get()
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    // ─── Suspend ─────────────────────────────────────────────────────────────

    @Test
    fun `Jackson3 exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j3-await.ndjson".toPath()
        val report = Jackson3NdJsonBulkExporter().exportGraphAwait(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesWritten shouldBeEqualTo 2L
    }

    @Test
    fun `Jackson3 importGraphFlow emits progress`() = runSuspendIO {
        val path = "/j3-flow.ndjson".toPath()
        Jackson3NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val events = Jackson3NdJsonBulkImporter().importGraphFlow(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        ).toList()
        events shouldHaveSize 2
    }

    @Test
    fun `Jackson3 importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j3-await-import.ndjson".toPath()
        Jackson3NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val report = Jackson3NdJsonBulkImporter().importGraphAwait(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    // ─── Jackson 2 Suspend / Flow ─────────────────────────────────────────────

    @Test
    fun `Jackson2 exportGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j2-await.ndjson".toPath()
        val report = Jackson2NdJsonBulkExporter().exportGraphAwait(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `Jackson2 importGraphAwait returns completed report`() = runSuspendIO {
        val path = "/j2-await-import.ndjson".toPath()
        Jackson2NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val report = Jackson2NdJsonBulkImporter().importGraphAwait(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        )
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `Jackson2 exportGraphFlow emits progress`() = runSuspendIO {
        val path = "/j2-flow-export.ndjson".toPath()
        val events = Jackson2NdJsonBulkExporter().exportGraphFlow(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        ).toList()
        events shouldHaveSize 2
    }

    @Test
    fun `Jackson2 importGraphFlow emits progress`() = runSuspendIO {
        val path = "/j2-flow-import.ndjson".toPath()
        Jackson2NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val events = Jackson2NdJsonBulkImporter().importGraphFlow(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        ).toList()
        events shouldHaveSize 2
    }

    @Test
    fun `Jackson2 importGraphAsync returns completed report`() {
        val path = "/j2-vt-import.ndjson".toPath()
        Jackson2NdJsonBulkExporter().exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        )
        val report = Jackson2NdJsonBulkImporter().importGraphAsync(
            OkioGraphImportSource.PathSource(path, fakeFs),
            TinkerGraphOperations(), GraphImportOptions(),
        ).get()
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `Jackson3 exportGraphFlow emits progress`() = runSuspendIO {
        val path = "/j3-flow-export.ndjson".toPath()
        val events = Jackson3NdJsonBulkExporter().exportGraphFlow(
            OkioGraphExportSink.PathSink(path, fakeFs), buildSourceGraph(), exportOptions,
        ).toList()
        events shouldHaveSize 2
    }
}

package io.bluetape4k.graph.io.okio.extension

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
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.amshove.kluent.shouldBeEqualTo
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
}

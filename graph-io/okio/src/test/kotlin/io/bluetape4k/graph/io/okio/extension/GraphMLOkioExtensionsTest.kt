package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
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
}

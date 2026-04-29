package io.bluetape4k.graph.io.okio

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class OkioRoundTripTest {

    private val fakeFs = FakeFileSystem()
    private val importer = OkioGraphBulkImporter()
    private val exporter = OkioGraphBulkExporter()

    @AfterEach
    fun cleanup() {
        fakeFs.checkNoOpenFiles()
    }

    private fun buildSourceGraph(): TinkerGraphOperations {
        val ops = TinkerGraphOperations()
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val charlie = ops.createVertex("Person", mapOf("name" to "Charlie"))
        ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2020"))
        ops.createEdge(bob.id, charlie.id, "KNOWS", mapOf("since" to "2022"))
        return ops
    }

    private val exportOptions = GraphExportOptions(
        vertexLabels = setOf("Person"),
        edgeLabels = setOf("KNOWS"),
    )

    @Test
    fun `NDJSON Jackson2 round trip`() {
        val path = "/graph.ndjson".toPath()
        val src = buildSourceGraph()

        exporter.exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            src,
            exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val report = importer.importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            target,
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `NDJSON Jackson3 round trip`() {
        val path = "/graph.ndjson".toPath()
        val src = buildSourceGraph()

        exporter.exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val report = importer.importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            target,
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `GraphML round trip`() {
        val path = "/graph.graphml".toPath()
        val src = buildSourceGraph()

        exporter.exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.GRAPHML,
            src,
            exportOptions,
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val report = importer.importGraph(
            OkioGraphImportSource.PathSource(path, fakeFs),
            GraphIoFormat.GRAPHML,
            target,
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `NDJSON Jackson3 gzip round trip`() {
        val path = "/graph.ndjson.gz".toPath()
        val src = buildSourceGraph()

        GraphIoOkioPaths.openGzipSink(
            OkioGraphExportSink.PathSink(path, fakeFs)
        ).use { bs ->
            // write using exporter via OutputStreamBased
            val os = bs.outputStream()
            val osSink = OkioGraphExportSink.OutputStreamBased(os, ownsStream = false)
            exporter.exportGraph(osSink, GraphIoFormat.NDJSON_JACKSON3, src, exportOptions)
        }

        val target = TinkerGraphOperations()
        val report = GraphIoOkioPaths.openGzipSource(
            OkioGraphImportSource.PathSource(path, fakeFs)
        ).use { bs ->
            val is_ = bs.inputStream()
            val isSource = OkioGraphImportSource.InputStreamBased(is_, ownsStream = false)
            importer.importGraph(isSource, GraphIoFormat.NDJSON_JACKSON3, target, GraphImportOptions())
        }

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }
}

package io.bluetape4k.graph.io.okio

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.GraphUnsupportedOperationException
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.tink.daead.TinkDaeads
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class OkioRoundTripTest: AbstractOkioTest() {

    companion object: KLogging()

    private val importer = OkioGraphBulkImporter.Default
    private val exporter = OkioGraphBulkExporter.Default

    override fun buildSourceGraph(): TinkerGraphOperations {
        val ops = TinkerGraphOperations()
        val alice = ops.createVertex("Person", "name" to "Alice")
        val bob = ops.createVertex("Person", "name" to "Bob")
        val charlie = ops.createVertex("Person", "name" to "Charlie")

        ops.createEdge(alice.id, bob.id, "KNOWS", "since" to "2020")
        ops.createEdge(bob.id, charlie.id, "KNOWS", "since" to "2022")

        return ops
    }

    @Test
    fun `NDJSON Jackson2 round trip`() {
        val path = "/graph.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = exporter.exportGraph(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            src,
            exportOptions,
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 3L
        exportReport.edgesWritten shouldBeEqualTo 2L

        val target = TinkerGraphOperations()
        val importResult = importer.importGraph(
            path.asOkioSource(fakeFs),
            GraphIoFormat.NDJSON_JACKSON2,
            target,
            GraphImportOptions(),
        )
        log.debug { "import report=$importResult" }
        importResult.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importResult.verticesCreated shouldBeEqualTo 3L
        importResult.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `NDJSON Jackson3 round trip`() {
        val path = "/graph.ndjson".toPath()
        val src = buildSourceGraph()

        val exportReport = exporter.exportGraph(
            path.asOkioSink(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            src,
            exportOptions,
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 3L
        exportReport.edgesWritten shouldBeEqualTo 2L

        val target = TinkerGraphOperations()
        val importReport = importer.importGraph(
            path.asOkioSource(fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            target,
            GraphImportOptions(),
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 3L
        importReport.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `GraphML round trip`() {
        val path = "/graph.graphml".toPath()
        val src = buildSourceGraph()

        val exportReport = exporter.exportGraph(
            path.asOkioSink(fakeFs),
            GraphIoFormat.GRAPHML,
            src,
            exportOptions,
        )
        log.debug { "export report=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 3L
        exportReport.edgesWritten shouldBeEqualTo 2L

        val target = TinkerGraphOperations()
        val importReport = importer.importGraph(
            path.asOkioSource(fakeFs),
            GraphIoFormat.GRAPHML,
            target,
            GraphImportOptions(),
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 3L
        importReport.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `NDJSON Jackson3 gzip round trip`() {
        val path = "/graph.ndjson.gz".toPath()
        val src = buildSourceGraph()

        val exportResult = path.asOkioSink(fakeFs).openGzipSink().use { bs ->
            val osSink = bs.outputStream().asOkioSink(ownsStream = false)
            exporter.exportGraph(osSink, GraphIoFormat.NDJSON_JACKSON3, src, exportOptions)
        }
        log.debug { "export result=$exportResult" }
        exportResult.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportResult.verticesWritten shouldBeEqualTo 3L
        exportResult.edgesWritten shouldBeEqualTo 2L

        val target = TinkerGraphOperations()
        val importReport = path.asOkioSource(fakeFs).openGzipSource().use { bs ->
            val isSource = bs.inputStream().asOkioSource(ownsStream = false)
            importer.importGraph(isSource, GraphIoFormat.NDJSON_JACKSON3, target, GraphImportOptions.Default)
        }

        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 3L
        importReport.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `NDJSON Jackson3 DAEAD round trip`() {
        val path = "/graph.ndjson.enc".toPath()
        val associatedData = "graph-okio-daead".encodeToByteArray()
        val src = buildSourceGraph()

        val exportReport = exporter.exportGraphDaead(
            sink = path.asOkioSink(fakeFs),
            format = GraphIoFormat.NDJSON_JACKSON3,
            daead = TinkDaeads.AES256_SIV,
            operations = src,
            options = exportOptions,
            associatedData = associatedData,
        )
        log.debug { "export result=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 3L
        exportReport.edgesWritten shouldBeEqualTo 2L

        val target = TinkerGraphOperations()

        val importReport = importer.importGraphDaead(
            source = path.asOkioSource(fakeFs),
            format = GraphIoFormat.NDJSON_JACKSON3,
            daead = TinkDaeads.AES256_SIV,
            operations = target,
            options = GraphImportOptions(),
            associatedData = associatedData,
        )

        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 3L
        importReport.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `NDJSON Jackson3 gzip DAEAD round trip`() {
        val path = "/graph.ndjson.gz.enc".toPath()
        val associatedData = "graph-okio-gzip-daead".encodeToByteArray()
        val src = buildSourceGraph()

        val exportReport = exporter.exportGraphGzipDaead(
            sink = path.asOkioSink(fakeFs),
            format = GraphIoFormat.NDJSON_JACKSON3,
            daead = TinkDaeads.AES256_SIV,
            operations = src,
            options = exportOptions,
            associatedData = associatedData,
        )
        log.debug { "export result=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 3L
        exportReport.edgesWritten shouldBeEqualTo 2L

        val target = TinkerGraphOperations()

        val importReport = importer.importGraphDaeadGzip(
            source = path.asOkioSource(fakeFs),
            format = GraphIoFormat.NDJSON_JACKSON3,
            daead = TinkDaeads.AES256_SIV,
            operations = target,
            options = GraphImportOptions(),
            associatedData = associatedData,
        )
        log.debug { "import report=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 3L
        importReport.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `DAEAD export rejects CSV paired format`() {
        assertFailsWith<GraphUnsupportedOperationException> {
            exporter.exportGraphDaead(
                sink = OkioGraphExportSink.PathSink("/graph.enc".toPath(), fakeFs),
                format = GraphIoFormat.CSV,
                daead = TinkDaeads.AES256_SIV,
                operations = buildSourceGraph(),
            )
        }
    }

    @Test
    fun `gzip DAEAD export rejects CSV paired format`() {
        assertFailsWith<GraphUnsupportedOperationException> {
            exporter.exportGraphGzipDaead(
                sink = OkioGraphExportSink.PathSink("/graph.gz.enc".toPath(), fakeFs),
                format = GraphIoFormat.CSV,
                daead = TinkDaeads.AES256_SIV,
                operations = buildSourceGraph(),
            )
        }
    }

    @Test
    fun `DAEAD import rejects CSV paired format`() {
        assertFailsWith<GraphUnsupportedOperationException> {
            importer.importGraphDaead(
                source = OkioGraphImportSource.PathSource("/graph.enc".toPath(), fakeFs),
                format = GraphIoFormat.CSV,
                daead = TinkDaeads.AES256_SIV,
                operations = TinkerGraphOperations(),
            )
        }
    }

    @Test
    fun `gzip DAEAD import rejects CSV paired format`() {
        assertFailsWith<GraphUnsupportedOperationException> {
            importer.importGraphDaeadGzip(
                source = OkioGraphImportSource.PathSource("/graph.gz.enc".toPath(), fakeFs),
                format = GraphIoFormat.CSV,
                daead = TinkDaeads.AES256_SIV,
                operations = TinkerGraphOperations(),
            )
        }
    }
}

package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.testsupport.FakeGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.ExecutionException

class VirtualThreadGraphBulkAdapterTest {

    private val stubImportReport = GraphImportReport(
        GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, 0L, 0L, 0L, 0L, 0L, Duration.ZERO
    )
    private val stubExportReport = GraphExportReport(
        status = GraphIoStatus.COMPLETED,
        format = GraphIoFormat.CSV,
        verticesWritten = 0L,
        edgesWritten = 0L,
        elapsed = Duration.ZERO,
    )

    private fun stubImporter(block: (String, GraphOperations, GraphImportOptions) -> GraphImportReport) =
        object : GraphBulkImporter<String> {
            override fun importGraph(source: String, operations: GraphOperations, options: GraphImportOptions) =
                block(source, operations, options)
        }

    private fun stubExporter(block: (String, GraphOperations, GraphExportOptions) -> GraphExportReport) =
        object : GraphBulkExporter<String> {
            override fun exportGraph(sink: String, operations: GraphOperations, options: GraphExportOptions) =
                block(sink, operations, options)
        }

    // ── wrapImporter ─────────────────────────────────────────────────────────

    @Test
    fun `importAsync wraps sync importer with virtual thread future`() {
        val importer = stubImporter { _, _, _ -> stubImportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        vt.importGraphAsync("src", FakeGraphOperations(), GraphImportOptions()).get() shouldBeEqualTo stubImportReport
    }

    @Test
    fun `importAsync with default options uses interface default parameter`() {
        val importer = stubImporter { _, _, _ -> stubImportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        // omit options → triggers GraphVirtualThreadBulkImporter DefaultImpls stub
        vt.importGraphAsync("src", FakeGraphOperations()).get() shouldBeEqualTo stubImportReport
    }

    @Test
    fun `importAsync propagates sync failure`() {
        val boom = RuntimeException("boom")
        val importer = stubImporter { _, _, _ -> throw boom }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        val ee = assertThrows<ExecutionException> {
            vt.importGraphAsync("x", FakeGraphOperations(), GraphImportOptions()).get()
        }
        ee.cause shouldBeInstanceOf RuntimeException::class
    }

    // ── wrapExporter ─────────────────────────────────────────────────────────

    @Test
    fun `exportAsync wraps sync exporter with virtual thread future`() {
        val exporter = stubExporter { _, _, _ -> stubExportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapExporter(exporter)
        vt.exportGraphAsync("sink", FakeGraphOperations(), GraphExportOptions()).get() shouldBeEqualTo stubExportReport
    }

    @Test
    fun `exportAsync with default options uses interface default parameter`() {
        val exporter = stubExporter { _, _, _ -> stubExportReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapExporter(exporter)
        // omit options → triggers GraphVirtualThreadBulkExporter DefaultImpls stub
        vt.exportGraphAsync("sink", FakeGraphOperations()).get() shouldBeEqualTo stubExportReport
    }

    @Test
    fun `exportAsync propagates sync failure`() {
        val boom = RuntimeException("export boom")
        val exporter = stubExporter { _, _, _ -> throw boom }
        val vt = VirtualThreadGraphBulkAdapter.wrapExporter(exporter)
        val ee = assertThrows<ExecutionException> {
            vt.exportGraphAsync("sink", FakeGraphOperations(), GraphExportOptions()).get()
        }
        ee.cause shouldBeInstanceOf RuntimeException::class
    }
}

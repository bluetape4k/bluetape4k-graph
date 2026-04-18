package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
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

    private val stubReport = GraphImportReport(
        GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, 0L, 0L, 0L, 0L, 0L, Duration.ZERO
    )

    private fun stubImporter(block: (String, GraphOperations, GraphImportOptions) -> GraphImportReport) =
        object : GraphBulkImporter<String> {
            override fun importGraph(source: String, operations: GraphOperations, options: GraphImportOptions) =
                block(source, operations, options)
        }

    @Test
    fun `importAsync wraps sync importer with virtual thread future`() {
        val importer = stubImporter { _, _, _ -> stubReport }
        val vt = VirtualThreadGraphBulkAdapter.wrapImporter(importer)
        vt.importGraphAsync("src", FakeGraphOperations(), GraphImportOptions()).get() shouldBeEqualTo stubReport
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
}

package io.bluetape4k.graph.io.csv

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/** CSV Virtual Thread 기반 익스포터. Sync 익스포터를 VT Future로 감싼다. */
class CsvGraphVirtualThreadBulkExporter(
    private val sync: CsvGraphBulkExporter = CsvGraphBulkExporter(),
) : GraphVirtualThreadBulkExporter<CsvGraphExportSink> {

    override fun exportGraphAsync(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)

    fun exportGraphAsync(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): CompletableFuture<GraphExportReport> =
        virtualFutureOf { sync.exportGraph(sink, operations, options, csvOptions) }

    companion object : KLogging()
}

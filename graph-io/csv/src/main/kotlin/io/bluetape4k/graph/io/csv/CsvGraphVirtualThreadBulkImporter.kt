package io.bluetape4k.graph.io.csv

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/** CSV Virtual Thread 기반 임포터. Sync 임포터를 VT Future로 감싼다. */
class CsvGraphVirtualThreadBulkImporter(
    private val sync: CsvGraphBulkImporter = CsvGraphBulkImporter(),
) : GraphVirtualThreadBulkImporter<CsvGraphImportSource> {

    override fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.wrapImporter(sync).importGraphAsync(source, operations, options)

    fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): CompletableFuture<GraphImportReport> =
        virtualFutureOf { sync.importGraph(source, operations, options, csvOptions) }

    companion object : KLogging()
}

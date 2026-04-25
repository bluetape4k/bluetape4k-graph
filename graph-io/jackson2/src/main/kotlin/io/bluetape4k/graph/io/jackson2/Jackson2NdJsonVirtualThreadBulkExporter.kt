package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

class Jackson2NdJsonVirtualThreadBulkExporter : GraphVirtualThreadBulkExporter<GraphExportSink> {

    private val sync: Jackson2NdJsonBulkExporter = Jackson2NdJsonBulkExporter()

    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)

    companion object : KLogging()
}

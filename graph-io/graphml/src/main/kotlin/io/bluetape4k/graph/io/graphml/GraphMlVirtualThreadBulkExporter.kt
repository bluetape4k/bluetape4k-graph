package io.bluetape4k.graph.io.graphml

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/** GraphML Virtual Thread 기반 익스포터. Sync 익스포터를 VT Future로 감싼다. */
class GraphMlVirtualThreadBulkExporter(
    private val sync: GraphMlBulkExporter = GraphMlBulkExporter(),
) : GraphVirtualThreadBulkExporter<GraphExportSink> {

    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)

    fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
    ): CompletableFuture<GraphExportReport> =
        virtualFutureOf { sync.exportGraph(sink, operations, options, graphMlOptions) }

    companion object : KLogging()
}

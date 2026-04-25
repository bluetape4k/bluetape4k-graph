package io.bluetape4k.graph.io.graphml

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/** GraphML Virtual Thread 기반 임포터. Sync 임포터를 VT Future로 감싼다. */
class GraphMlVirtualThreadBulkImporter(
    private val sync: GraphMlBulkImporter = GraphMlBulkImporter(),
) : GraphVirtualThreadBulkImporter<GraphImportSource> {

    override fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.wrapImporter(sync).importGraphAsync(source, operations, options)

    fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): CompletableFuture<GraphImportReport> =
        virtualFutureOf { sync.importGraph(source, operations, options, graphMlOptions) }

    companion object : KLogging()
}

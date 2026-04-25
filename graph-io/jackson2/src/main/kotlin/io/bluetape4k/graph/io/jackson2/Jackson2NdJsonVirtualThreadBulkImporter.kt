package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

class Jackson2NdJsonVirtualThreadBulkImporter : GraphVirtualThreadBulkImporter<GraphImportSource> {

    private val sync: Jackson2NdJsonBulkImporter = Jackson2NdJsonBulkImporter()

    override fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.wrapImporter(sync).importGraphAsync(source, operations, options)

    companion object : KLogging()
}

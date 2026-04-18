package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import java.util.concurrent.CompletableFuture

/** Virtual Thread 기반 비동기 벌크 익스포터 계약. */
interface GraphVirtualThreadBulkExporter<T : Any> {
    fun exportGraphAsync(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): CompletableFuture<GraphExportReport>
}

package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import java.util.concurrent.CompletableFuture

/** Virtual Thread 기반 비동기 벌크 임포터 계약. */
interface GraphVirtualThreadBulkImporter<S : Any> {
    fun importGraphAsync(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): CompletableFuture<GraphImportReport>
}

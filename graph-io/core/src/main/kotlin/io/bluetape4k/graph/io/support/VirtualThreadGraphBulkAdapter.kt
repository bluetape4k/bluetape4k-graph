package io.bluetape4k.graph.io.support

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging

/**
 * Sync importer/exporter를 Virtual Thread 기반 CompletableFuture로 감싸는 어댑터.
 * 취소는 start 이전에만 effective하다.
 */
object VirtualThreadGraphBulkAdapter : KLogging() {

    fun <S : Any> wrapImporter(sync: GraphBulkImporter<S>): GraphVirtualThreadBulkImporter<S> =
        object : GraphVirtualThreadBulkImporter<S> {
            override fun importGraphAsync(
                source: S,
                operations: GraphOperations,
                options: GraphImportOptions,
            ) = virtualFutureOf { sync.importGraph(source, operations, options) }
        }

    fun <T : Any> wrapExporter(sync: GraphBulkExporter<T>): GraphVirtualThreadBulkExporter<T> =
        object : GraphVirtualThreadBulkExporter<T> {
            override fun exportGraphAsync(
                sink: T,
                operations: GraphOperations,
                options: GraphExportOptions,
            ) = virtualFutureOf { sync.exportGraph(sink, operations, options) }
        }
}

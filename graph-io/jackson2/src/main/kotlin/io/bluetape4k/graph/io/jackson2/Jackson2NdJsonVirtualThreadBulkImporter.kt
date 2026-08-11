package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * Jackson 2 기반 virtual-thread NDJSON bulk importer.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonVirtualThreadBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = Jackson2NdJsonVirtualThreadBulkImporter()
 * val future = importer.importGraphAsync(
 *     source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphImportOptions(batchSize = 500),
 * )
 * val report = future.join()
 * ```
 */
class Jackson2NdJsonVirtualThreadBulkImporter : GraphVirtualThreadBulkImporter<GraphImportSource> {

    private val sync: Jackson2NdJsonBulkImporter = Jackson2NdJsonBulkImporter()

    override fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.wrapImporter(sync).importGraphAsync(source, operations, options)

    override fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphImportReport> =
        runWithProgress(source, operations, options, listener)

    private fun runWithProgress(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphImportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.NDJSON_JACKSON2,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.importGraph(source, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    companion object : KLogging()
}

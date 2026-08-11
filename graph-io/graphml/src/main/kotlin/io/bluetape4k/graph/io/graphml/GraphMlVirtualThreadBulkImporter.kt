package io.bluetape4k.graph.io.graphml

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

/** GraphML Virtual Thread 기반 임포터. Sync 임포터를 VT Future로 감싼다. */
/**
 * Virtual-thread bulk importer for GraphML.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlVirtualThreadBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = GraphMlVirtualThreadBulkImporter()
 * val future = importer.importGraphAsync(
 *     source = GraphImportSource.PathSource(Paths.get("graph.graphml")),
 *     operations = graphOps,
 *     options = GraphImportOptions(batchSize = 250),
 * )
 * val report = future.join()
 * ```
 */
class GraphMlVirtualThreadBulkImporter(
    private val sync: GraphMlBulkImporter = GraphMlBulkImporter(),
) : GraphVirtualThreadBulkImporter<GraphImportSource> {

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
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.importGraph(source, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.cancellableVirtualFuture {
            sync.importGraph(source, operations, options, graphMlOptions)
        }

    fun importGraphAsync(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphImportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.importGraph(source, operations, options, graphMlOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    companion object : KLogging()
}

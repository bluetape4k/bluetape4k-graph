package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.asVirtualThreadExporter
import io.bluetape4k.graph.io.support.cancellableVirtualFuture
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/** GraphML Virtual Thread 기반 익스포터. Sync 익스포터를 VT Future로 감싼다. */
/**
 * Virtual-thread bulk exporter for GraphML.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlVirtualThreadBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter = GraphMlVirtualThreadBulkExporter()
 * val future = exporter.exportGraphAsync(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.graphml")),
 *     operations = graphOps,
 *     options = GraphExportOptions(edgeLabels = setOf("KNOWS")),
 * )
 * val report = future.join()
 * ```
 */
class GraphMlVirtualThreadBulkExporter(
    private val sync: GraphMlBulkExporter = GraphMlBulkExporter.Default,
): GraphVirtualThreadBulkExporter<GraphExportSink> {

    companion object: KLogging() {
        val Default = GraphMlVirtualThreadBulkExporter()
    }

    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        sync.asVirtualThreadExporter().exportGraphAsync(sink, operations, options)

    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphExportReport> =
        runWithProgress(sink, operations, options, listener)

    private fun runWithProgress(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphExportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(sink) },
        )
        return cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.exportGraph(sink, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
    ): CompletableFuture<GraphExportReport> =
        cancellableVirtualFuture {
            sync.exportGraph(sink, operations, options, graphMlOptions)
        }

    fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphExportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(sink) },
        )
        return cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.exportGraph(sink, operations, options, graphMlOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }
}

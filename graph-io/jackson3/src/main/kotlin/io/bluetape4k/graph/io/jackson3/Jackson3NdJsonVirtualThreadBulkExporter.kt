package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * Jackson 3 기반 virtual-thread NDJSON bulk exporter.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter = Jackson3NdJsonVirtualThreadBulkExporter()
 * val future = exporter.exportGraphAsync(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphExportOptions(edgeLabels = setOf("KNOWS")),
 * )
 * val report = future.join()
 * ```
 */
class Jackson3NdJsonVirtualThreadBulkExporter : GraphVirtualThreadBulkExporter<GraphExportSink> {

    private val sync: Jackson3NdJsonBulkExporter = Jackson3NdJsonBulkExporter()

    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)

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
            format = GraphIoFormat.NDJSON_JACKSON3,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(sink) },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.exportGraph(sink, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    companion object : KLogging()
}

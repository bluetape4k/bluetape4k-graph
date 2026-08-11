package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * CSV Virtual Thread 기반 익스포터.
 *
 * [CsvGraphBulkExporter]를 Java Virtual Thread 위에서 비동기로 실행한다.
 * `CompletableFuture`를 통해 논블로킹 방식으로 결과를 받을 수 있다.
 *
 * ```kotlin
 * val exporter = CsvGraphVirtualThreadBulkExporter()
 * val sink = CsvGraphExportSink(
 *     vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
 *     edges    = GraphExportSink.PathSink(Paths.get("edges.csv")),
 * )
 * val future = exporter.exportGraphAsync(sink, graphOps, GraphExportOptions(vertexLabels = setOf("Person")))
 * val report = future.get()  // 완료 대기
 * println("exported ${report.verticesWritten} vertices — ${report.status}")
 * ```
 */
class CsvGraphVirtualThreadBulkExporter(
    private val sync: CsvGraphBulkExporter = CsvGraphBulkExporter(),
) : GraphVirtualThreadBulkExporter<CsvGraphExportSink> {

    override fun exportGraphAsync(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)

    override fun exportGraphAsync(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphExportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(sink.vertices),
                    GraphIoPaths.sizeOf(sink.edges),
                )
            },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.exportGraph(sink, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    fun exportGraphAsync(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.cancellableVirtualFuture {
            sync.exportGraph(sink, operations, options, csvOptions)
        }

    fun exportGraphAsync(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphExportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(sink.vertices),
                    GraphIoPaths.sizeOf(sink.edges),
                )
            },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.exportGraph(sink, operations, options, csvOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    companion object : KLogging()
}

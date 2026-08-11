package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
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
 * CSV Virtual Thread 기반 임포터.
 *
 * [CsvGraphBulkImporter]를 Java Virtual Thread 위에서 비동기로 실행한다.
 * `CompletableFuture`를 통해 논블로킹 방식으로 결과를 받을 수 있다.
 *
 * ```kotlin
 * val importer = CsvGraphVirtualThreadBulkImporter()
 * val source = CsvGraphImportSource(
 *     vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
 *     edges    = GraphImportSource.PathSource(Paths.get("edges.csv")),
 * )
 * val future = importer.importGraphAsync(source, graphOps, GraphImportOptions())
 * val report = future.get()  // 완료 대기
 * println("imported ${report.verticesCreated} vertices — ${report.status}")
 * ```
 */
class CsvGraphVirtualThreadBulkImporter(
    private val sync: CsvGraphBulkImporter = CsvGraphBulkImporter(),
) : GraphVirtualThreadBulkImporter<CsvGraphImportSource> {

    override fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.wrapImporter(sync).importGraphAsync(source, operations, options)

    override fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphImportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(source.vertices),
                    GraphIoPaths.sizeOf(source.edges),
                )
            },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.importGraph(source, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): CompletableFuture<GraphImportReport> =
        VirtualThreadGraphBulkAdapter.cancellableVirtualFuture {
            sync.importGraph(source, operations, options, csvOptions)
        }

    fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
        listener: GraphIoProgressListener,
    ): CompletableFuture<GraphImportReport> {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(source.vertices),
                    GraphIoPaths.sizeOf(source.edges),
                )
            },
        )
        return VirtualThreadGraphBulkAdapter.cancellableVirtualFuture(
            reporter = reporter,
            block = { sync.importGraph(source, operations, options, csvOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    companion object : KLogging()
}

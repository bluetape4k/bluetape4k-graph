package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.graphml.internal.StaxGraphMlWriter
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.io.support.resolveLabels
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * Blocking bulk exporter for GraphML.
 *
 * exporter는 property key 이름만 미리 스캔한 뒤 repository API chunk를 이용해
 * 정점과 간선을 하나의 XML 문서에 기록한다. source bounded 실행은 graph facade가
 * `BOUNDED_CHUNKED_*` capability를 광고하는 경우에만 보장된다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
 * import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter = GraphMlBulkExporter()
 * val report = exporter.exportGraph(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.graphml")),
 *     operations = graphOps,
 *     options = GraphExportOptions(vertexLabels = setOf("Person")),
 *     graphMlOptions = GraphMlExportOptions(graphId = "social"),
 * )
 * check(report.verticesWritten >= 0)
 * ```
 */
class GraphMlBulkExporter : GraphBulkExporter<GraphExportSink> {

    private val writer = StaxGraphMlWriter()

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, operations, options, GraphMlExportOptions())

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): GraphExportReport = exportGraph(sink, operations, options, GraphMlExportOptions(), listener)

    fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
        listener: GraphIoProgressListener,
    ): GraphExportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(sink) },
        )
        return reporter.run(
            block = { exportGraph(sink, operations, options, graphMlOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
    ): GraphExportReport {
        log.debug { "Starting GRAPHML export: vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()
        val (vertexLabels, edgeLabels) = options.resolveLabels(operations)

        fun vertexChunks() = vertexLabels.asSequence().flatMap { label ->
            operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize)
        }

        fun edgeChunks() = edgeLabels.asSequence().flatMap { label ->
            operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize)
        }

        val vertexPropertyKeys = linkedSetOf<String>()
        for (chunk in vertexChunks()) {
            chunk.forEach { vertexPropertyKeys.addAll(it.properties.keys) }
        }
        val edgePropertyKeys = linkedSetOf<String>()
        for (chunk in edgeChunks()) {
            chunk.forEach { edgePropertyKeys.addAll(it.properties.keys) }
        }

        fun vertexRecords() = vertexChunks()
            .flatMap { chunk -> chunk.asSequence() }
            .map { v -> GraphIoVertexRecord(v.id.value, v.label, v.properties) }

        fun edgeRecords() = edgeChunks()
            .flatMap { chunk -> chunk.asSequence() }
            .map { e -> GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties) }

        val writeResult = GraphIoPaths.openOutputStream(sink).use { output ->
            writer.write(
                output = output,
                vertices = vertexRecords(),
                edges = edgeRecords(),
                options = graphMlOptions,
                vertexPropertyKeys = vertexPropertyKeys,
                edgePropertyKeys = edgePropertyKeys,
            )
        }

        return GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.GRAPHML,
            verticesWritten = writeResult.verticesWritten,
            edgesWritten = writeResult.edgesWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        ).also {
            log.debug {
                "Export completed: vertices=${writeResult.verticesWritten}, " +
                    "edges=${writeResult.edgesWritten}, elapsed=${watch.elapsed()}"
            }
        }
    }

    companion object : KLogging()
}

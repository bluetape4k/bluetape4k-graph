package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.jackson3.internal.Jackson3EnvelopeCodec
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
 * Jackson 3 기반 blocking NDJSON bulk exporter.
 *
 * vertex를 edge보다 먼저 쓰며, 각 graph record는 하나의 JSON
 * 한 줄을 차지한다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter = Jackson3NdJsonBulkExporter()
 * val report = exporter.exportGraph(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphExportOptions(edgeLabels = setOf("KNOWS")),
 * )
 * ```
 */
class Jackson3NdJsonBulkExporter : GraphBulkExporter<GraphExportSink> {

    private val codec: Jackson3EnvelopeCodec = Jackson3EnvelopeCodec()

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): GraphExportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.NDJSON_JACKSON3,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(sink) },
        )
        return reporter.run(
            block = { exportGraph(sink, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        log.debug { "Starting NDJSON_JACKSON3 export: vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L; var eWritten = 0L
        val (vertexLabels, edgeLabels) = options.resolveLabels(operations)

        GraphIoPaths.openWriter(sink).use { writer ->
            // 정점 쓰기
            for (label in vertexLabels) {
                for (chunk in operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize)) {
                    for (v in chunk) {
                        val rec = GraphIoVertexRecord(v.id.value, v.label, v.properties)
                        writer.write(codec.writeVertex(rec))
                        writer.newLine()
                        vWritten++
                    }
                }
            }
            // 간선 쓰기
            for (label in edgeLabels) {
                for (chunk in operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize)) {
                    for (e in chunk) {
                        val rec = GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
                        writer.write(codec.writeEdge(rec))
                        writer.newLine()
                        eWritten++
                    }
                }
            }
        }

        val status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL
        return GraphExportReport(
            status = status,
            format = GraphIoFormat.NDJSON_JACKSON3,
            verticesWritten = vWritten,
            edgesWritten = eWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        ).also {
            log.debug { "NDJSON_JACKSON3 export completed: verticesWritten=$vWritten, edgesWritten=$eWritten, status=$status, elapsed=${watch.elapsed()}" }
        }
    }

    companion object : KLogging()
}

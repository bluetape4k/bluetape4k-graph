package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.jackson2.internal.Jackson2EnvelopeCodec
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * Jackson2 기반 NDJSON 동기 벌크 익스포터.
 * 정점을 먼저 쓴 뒤 간선을 쓴다. 각 레코드는 한 줄의 JSON.
 */
class Jackson2NdJsonBulkExporter : GraphBulkExporter<GraphExportSink> {

    private val codec: Jackson2EnvelopeCodec = Jackson2EnvelopeCodec()

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        log.debug { "Starting NDJSON_JACKSON2 export: vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L; var eWritten = 0L

        GraphIoPaths.openWriter(sink).use { writer ->
            // 정점 쓰기
            for (label in options.vertexLabels) {
                for (v in operations.findVerticesByLabel(label)) {
                    val rec = GraphIoVertexRecord(v.id.value, v.label, v.properties)
                    writer.write(codec.writeVertex(rec))
                    writer.newLine()
                    vWritten++
                }
            }
            // 간선 쓰기
            for (label in options.edgeLabels) {
                for (e in operations.findEdgesByLabel(label)) {
                    val rec = GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
                    writer.write(codec.writeEdge(rec))
                    writer.newLine()
                    eWritten++
                }
            }
        }

        val status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL
        return GraphExportReport(
            status = status,
            format = GraphIoFormat.NDJSON_JACKSON2,
            verticesWritten = vWritten,
            edgesWritten = eWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        ).also {
            log.debug { "NDJSON_JACKSON2 export completed: verticesWritten=$vWritten, edgesWritten=$eWritten, status=$status, elapsed=${watch.elapsed()}" }
        }
    }

    companion object : KLogging()
}

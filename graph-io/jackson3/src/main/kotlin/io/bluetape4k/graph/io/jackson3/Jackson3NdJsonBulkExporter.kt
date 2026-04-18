package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.jackson3.internal.Jackson3EnvelopeCodec
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

/**
 * Jackson3 기반 NDJSON 동기 벌크 익스포터.
 * 정점을 먼저 쓴 뒤 간선을 쓴다. 각 레코드는 한 줄의 JSON.
 */
class Jackson3NdJsonBulkExporter : GraphBulkExporter<GraphExportSink> {

    private val codec: Jackson3EnvelopeCodec = Jackson3EnvelopeCodec()

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
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

        return GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.NDJSON_JACKSON3,
            verticesWritten = vWritten,
            edgesWritten = eWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        )
    }

    companion object : KLogging()
}

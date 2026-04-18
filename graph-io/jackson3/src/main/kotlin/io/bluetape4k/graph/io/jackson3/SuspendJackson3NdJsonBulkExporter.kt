package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.contract.GraphSuspendBulkExporter
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
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

class SuspendJackson3NdJsonBulkExporter : GraphSuspendBulkExporter<GraphExportSink> {

    private val codec: Jackson3EnvelopeCodec = Jackson3EnvelopeCodec()

    override suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = withContext(Dispatchers.IO) {
        log.debug { "Starting NDJSON_JACKSON3 export (suspend): vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L; var eWritten = 0L

        GraphIoPaths.openWriter(sink).use { writer ->
            for (label in options.vertexLabels) {
                for (v in operations.findVerticesByLabel(label).toList()) {
                    val rec = GraphIoVertexRecord(v.id.value, v.label, v.properties)
                    writer.write(codec.writeVertex(rec))
                    writer.newLine()
                    vWritten++
                }
            }
            for (label in options.edgeLabels) {
                for (e in operations.findEdgesByLabel(label).toList()) {
                    val rec = GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
                    writer.write(codec.writeEdge(rec))
                    writer.newLine()
                    eWritten++
                }
            }
        }

        val status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL
        GraphExportReport(
            status = status,
            format = GraphIoFormat.NDJSON_JACKSON3,
            verticesWritten = vWritten,
            edgesWritten = eWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        ).also {
            log.debug { "NDJSON_JACKSON3 export (suspend) completed: verticesWritten=$vWritten, edgesWritten=$eWritten, status=$status, elapsed=${watch.elapsed()}" }
        }
    }

    companion object : KLoggingChannel()
}

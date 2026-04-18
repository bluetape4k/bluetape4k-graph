package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphSuspendBulkExporter
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
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

class SuspendJackson2NdJsonBulkExporter : GraphSuspendBulkExporter<GraphExportSink> {

    private val codec: Jackson2EnvelopeCodec = Jackson2EnvelopeCodec()

    override suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = withContext(Dispatchers.IO) {
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

        GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.NDJSON_JACKSON2,
            verticesWritten = vWritten,
            edgesWritten = eWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        )
    }

    companion object : KLoggingChannel()
}

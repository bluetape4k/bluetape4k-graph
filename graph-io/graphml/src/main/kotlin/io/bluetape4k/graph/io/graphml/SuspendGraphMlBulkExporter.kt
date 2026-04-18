package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphSuspendBulkExporter
import io.bluetape4k.graph.io.graphml.internal.StaxGraphMlWriter
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

/**
 * GraphML 코루틴(suspend) 벌크 익스포터.
 * Flow로 정점/간선을 수집한 뒤 StAX 라이터로 XML 파일에 기록한다.
 */
class SuspendGraphMlBulkExporter : GraphSuspendBulkExporter<GraphExportSink> {

    private val writer = StaxGraphMlWriter()

    override suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, GraphMlExportOptions())

    suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
    ): GraphExportReport = withContext(Dispatchers.IO) {
        log.debug { "Starting GRAPHML suspend export" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()

        val vertices = options.vertexLabels.flatMap { label ->
            operations.findVerticesByLabel(label).toList().map { v ->
                GraphIoVertexRecord(v.id.value, v.label, v.properties)
            }
        }
        val edges = options.edgeLabels.flatMap { label ->
            operations.findEdgesByLabel(label).toList().map { e ->
                GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
            }
        }

        GraphIoPaths.openOutputStream(sink).use { output ->
            writer.write(output, vertices, edges, graphMlOptions)
        }

        GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.GRAPHML,
            verticesWritten = vertices.size.toLong(),
            edgesWritten = edges.size.toLong(),
            elapsed = watch.elapsed(),
            failures = failures,
        ).also { log.debug { "Suspend export completed: vertices=${vertices.size}, edges=${edges.size}" } }
    }

    companion object : KLoggingChannel()
}

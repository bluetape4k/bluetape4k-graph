package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphBulkExporter
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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * GraphML 동기 벌크 익스포터.
 * 정점과 간선을 모두 수집한 뒤 StAX 라이터로 단일 XML 파일에 기록한다.
 */
class GraphMlBulkExporter : GraphBulkExporter<GraphExportSink> {

    private val writer = StaxGraphMlWriter()

    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, operations, options, GraphMlExportOptions())

    fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
    ): GraphExportReport {
        log.debug { "Starting GRAPHML export: vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()

        val vertices = options.vertexLabels.flatMap { label ->
            operations.findVerticesByLabel(label).map { v ->
                GraphIoVertexRecord(v.id.value, v.label, v.properties)
            }
        }
        val edges = options.edgeLabels.flatMap { label ->
            operations.findEdgesByLabel(label).map { e ->
                GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
            }
        }

        GraphIoPaths.openOutputStream(sink).use { output ->
            writer.write(output, vertices, edges, graphMlOptions)
        }

        return GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.GRAPHML,
            verticesWritten = vertices.size.toLong(),
            edgesWritten = edges.size.toLong(),
            elapsed = watch.elapsed(),
            failures = failures,
        ).also { log.debug { "Export completed: vertices=${vertices.size}, edges=${edges.size}, elapsed=${watch.elapsed()}" } }
    }

    companion object : KLogging()
}

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
import io.bluetape4k.graph.io.support.GraphIoRecordSpool
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.io.support.resolveLabels
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * Blocking bulk exporter for GraphML.
 *
 * exporter는 bounded repository chunk를 immutable disk spool에 한 번 기록한 뒤,
 * 같은 snapshot에서 property key와 payload를 읽어 하나의 XML 문서에 기록한다.
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
        val spool = GraphIoRecordSpool()

        try {
            for (label in vertexLabels) {
                operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize).forEach { chunk ->
                    spool.appendVertices(chunk.map { v -> GraphIoVertexRecord(v.id.value, v.label, v.properties) })
                }
            }
            for (label in edgeLabels) {
                operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize).forEach { chunk ->
                    spool.appendEdges(
                        chunk.map { e ->
                            GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
                        },
                    )
                }
            }
            spool.finish()

            val writeResult = GraphIoPaths.openOutputStream(sink).use { output ->
                writer.write(
                    output = output,
                    vertices = spool.vertexRecords(),
                    edges = spool.edgeRecords(),
                    options = graphMlOptions,
                    vertexPropertyKeys = spool.vertexPropertyKeys,
                    edgePropertyKeys = spool.edgePropertyKeys,
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
        } finally {
            spool.close()
        }
    }

    companion object : KLogging()
}

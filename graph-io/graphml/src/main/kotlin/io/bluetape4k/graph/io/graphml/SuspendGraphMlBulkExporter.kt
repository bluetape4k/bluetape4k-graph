package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphSuspendBulkExporter
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
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/**
 * Coroutine bulk exporter for GraphML.
 *
 * Graph reads stay in the caller coroutine context while blocking StAX writes
 * are isolated on [Dispatchers.IO].
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.SuspendGraphMlBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter = SuspendGraphMlBulkExporter()
 * val report = exporter.exportGraphSuspending(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.graphml")),
 *     operations = suspendGraphOps,
 *     options = GraphExportOptions(vertexLabels = setOf("Person")),
 * )
 * ```
 */
class SuspendGraphMlBulkExporter : GraphSuspendBulkExporter<GraphExportSink> {

    private val writer = StaxGraphMlWriter()

    override suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, GraphMlExportOptions())

    override suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, GraphMlExportOptions(), listener)

    suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
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
        return reporter.runSuspending(
            block = { exportGraphSuspending(sink, operations, options, graphMlOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    suspend fun exportGraphSuspending(
        sink: GraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
        graphMlOptions: GraphMlExportOptions = GraphMlExportOptions(),
    ): GraphExportReport {
        log.debug { "Starting GRAPHML suspend export" }
        val watch = GraphIoStopwatch()
        val failures = mutableListOf<GraphIoFailure>()
        val (vertexLabels, edgeLabels) = options.resolveLabels(operations)

        val vertices = vertexLabels.flatMap { label ->
            operations.findVerticesByLabel(label).toList().map { v ->
                GraphIoVertexRecord(v.id.value, v.label, v.properties)
            }
        }
        val edges = edgeLabels.flatMap { label ->
            operations.findEdgesByLabel(label).toList().map { e ->
                GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
            }
        }

        withContext(Dispatchers.IO) {
            GraphIoPaths.openOutputStream(sink).use { output ->
                writer.write(output, vertices, edges, graphMlOptions)
            }
        }

        return GraphExportReport(
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

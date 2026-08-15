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
import kotlinx.coroutines.NonCancellable
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

    @Suppress("LongMethod", "TooGenericExceptionCaught")
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

        val vertexPropertyKeys = linkedSetOf<String>()
        for (label in vertexLabels) {
            operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize).collect { chunk ->
                chunk.forEach { vertexPropertyKeys.addAll(it.properties.keys) }
            }
        }
        val edgePropertyKeys = linkedSetOf<String>()
        for (label in edgeLabels) {
            operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize).collect { chunk ->
                chunk.forEach { edgePropertyKeys.addAll(it.properties.keys) }
            }
        }

        val output = withContext(Dispatchers.IO) { GraphIoPaths.openOutputStream(sink) }
        val session = try {
            withContext(Dispatchers.IO) {
                writer.open(output, graphMlOptions, vertexPropertyKeys, edgePropertyKeys)
            }
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { output.close() }
            throw e
        }

        val writeResult = try {
            for (label in vertexLabels) {
                operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize).collect { chunk ->
                    val records = chunk.map { v -> GraphIoVertexRecord(v.id.value, v.label, v.properties) }
                    withContext(Dispatchers.IO) { session.writeVertices(records) }
                }
            }
            for (label in edgeLabels) {
                operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize).collect { chunk ->
                    val records = chunk.map { e ->
                        GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
                    }
                    withContext(Dispatchers.IO) { session.writeEdges(records) }
                }
            }
            withContext(Dispatchers.IO) {
                session.finish()
                session.result()
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                session.close()
                output.close()
            }
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
                "Suspend export completed: vertices=${writeResult.verticesWritten}, " +
                    "edges=${writeResult.edgesWritten}"
            }
        }
    }

    companion object : KLoggingChannel()
}

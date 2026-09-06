package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphSuspendBulkImporter
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointIdentity
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointSession
import io.bluetape4k.graph.io.graphml.internal.StaxGraphMlReader
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.io.support.SuspendGraphIoBatchWriter
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect

/**
 * Coroutine bulk importer for GraphML.
 *
 * StAX parsing runs on [Dispatchers.IO]. Vertex and edge creation stays in the
 * caller coroutine context so backend implementations keep control over their
 * own dispatcher policy.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.SuspendGraphMlBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = SuspendGraphMlBulkImporter()
 * val report = importer.importGraphSuspending(
 *     source = GraphImportSource.PathSource(Paths.get("graph.graphml")),
 *     operations = suspendGraphOps,
 *     options = GraphImportOptions(batchSize = 250),
 * )
 * ```
 */
class SuspendGraphMlBulkImporter : GraphSuspendBulkImporter<GraphImportSource> {

    private val reader = StaxGraphMlReader()

    override suspend fun importGraphSuspending(
        source: GraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraphSuspending(source, operations, options, GraphMlImportOptions())

    override suspend fun importGraphSuspending(
        source: GraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport = importGraphSuspending(source, operations, options, GraphMlImportOptions(), listener)

    suspend fun importGraphSuspending(
        source: GraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return reporter.runSuspending(
            block = { importGraphSuspending(source, operations, options, graphMlOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    suspend fun importGraphSuspending(
        source: GraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): GraphImportReport {
        log.debug { "Starting GRAPHML suspend import" }
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val checkpoint = GraphImportCheckpointSession(
            format = GraphIoFormat.GRAPHML,
            sourceIdentity = GraphImportCheckpointIdentity.resolve(options, source),
            options = options,
            idMap = idMap,
            importOptionsIdentity = GraphImportCheckpointIdentity.optionsIdentity(
                options,
                graphMlOptions.labelAttrName,
                graphMlOptions.unsupportedElementPolicy.name,
                graphMlOptions.defaultVertexLabel,
                graphMlOptions.defaultEdgeLabel,
            ),
        )
        val batchWriter = SuspendGraphIoBatchWriter(operations, options.writeBatchSize) { boundary, error ->
            checkpoint.failed(boundary, error.message)
        }
        try {
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<io.bluetape4k.graph.io.model.GraphIoEdgeRecord>()
        var vr = 0L
        var vc = 0L
        var er = 0L
        var ec = 0L
        var sv = 0L
        var se = 0L
        var status = GraphIoStatus.COMPLETED
        var failureBoundary = "VERTICES"
        val coroutineContext = currentCoroutineContext()

        try {
            GraphIoPaths.openInputStream(source).use { input ->
                reader.events(input, graphMlOptions).collect { event ->
                    coroutineContext.ensureActive()
                    when (event) {
                        is StaxGraphMlReader.GraphMlRecordEvent.Vertex -> {
                            vr++
                            if (status == GraphIoStatus.FAILED) return@collect
                            if (checkpoint.shouldSkipVertex(vr)) return@collect
                            val v = event.record
                            val props = options.preserveExternalIdProperty
                                ?.let { v.properties + (it to v.externalId) } ?: v.properties
                            when (idMap.putFirstOrFail(v.externalId, GraphElementId(v.externalId))) {
                                GraphIoExternalIdMap.PutResult.CREATED -> {
                                    val created = batchWriter.addVertex(v.externalId, v.label, props, idMap)
                                    vc += created
                                    if (created > 0 || options.checkpointStore != null) {
                                        checkpoint.verticesCommitted(vr)
                                    }
                                }
                                GraphIoExternalIdMap.PutResult.SKIPPED -> {
                                    sv++
                                    if (options.checkpointStore != null) {
                                        checkpoint.verticesCommitted(vr)
                                    }
                                    status = GraphIoStatus.PARTIAL
                                    failures += GraphIoFailure(
                                        phase = GraphIoPhase.CREATE_VERTEX,
                                        severity = GraphIoFailureSeverity.WARN,
                                        fileRole = GraphIoFileRole.UNIFIED,
                                        recordId = v.externalId,
                                        message = "Duplicate vertex skipped: ${v.externalId}",
                                    ).also { log.warn { "Duplicate vertex skipped: ${v.externalId}" } }
                                }
                            }
                        }
                        is StaxGraphMlReader.GraphMlRecordEvent.Edge -> {
                            er++
                            if (status == GraphIoStatus.FAILED) return@collect
                            if (checkpoint.shouldSkipEdge(er)) return@collect
                            failureBoundary = "EDGES"
                            bufferedEdges += event.record
                            if (bufferedEdges.size > options.maxEdgeBufferSize) {
                                failures += GraphIoFailure(
                                    phase = GraphIoPhase.READ_EDGE,
                                    fileRole = GraphIoFileRole.UNIFIED,
                                    location = "edge-buffer:${bufferedEdges.size}",
                                    message = "Edge buffer exceeded maxEdgeBufferSize=${options.maxEdgeBufferSize}; " +
                                        "verticesCreated=$vc remain in graph as partial state",
                                )
                                status = GraphIoStatus.FAILED
                                throw StopImport
                            }
                        }
                        is StaxGraphMlReader.GraphMlRecordEvent.Failure -> {
                            failures += event.failure
                            failureBoundary = if (event.failure.phase == GraphIoPhase.READ_EDGE) "EDGES" else "VERTICES"
                            status = when {
                                event.failure.severity == GraphIoFailureSeverity.ERROR -> GraphIoStatus.FAILED
                                status == GraphIoStatus.COMPLETED -> GraphIoStatus.PARTIAL
                                else -> status
                            }
                        }
                    }
                }
            }
        } catch (_: StopImport) {
            // Stop parsing after a terminal edge-buffer failure.
        }

        if (status == GraphIoStatus.FAILED) {
            checkpoint.failed(failureBoundary)
            return GraphImportReport(
                status, GraphIoFormat.GRAPHML, vr, vc, er, ec, sv, se, watch.elapsed(), failures
            )
        }
        vc += batchWriter.flushVertices(idMap)
        checkpoint.verticesCommitted(vr)
        var drainedEdges = checkpoint.resumeEdgesProcessed
        while (bufferedEdges.isNotEmpty()) {
            val e = bufferedEdges.removeFirst()
            drainedEdges++
            val from = idMap.resolve(e.fromExternalId)
            val to = idMap.resolve(e.toExternalId)
            if (from == null || to == null) {
                when (options.onMissingEdgeEndpoint) {
                    MissingEndpointPolicy.FAIL -> {
                        ec += batchWriter.flushEdges()
                        checkpoint.edgesCommitted(vr, drainedEdges - 1)
                        val failure = GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            fileRole = GraphIoFileRole.UNIFIED,
                            recordId = e.externalId,
                            message = "Unresolved endpoint from=${e.fromExternalId} to=${e.toExternalId}",
                        )
                        log.warn { failure.message }
                        failures += failure
                        status = GraphIoStatus.FAILED
                        break
                    }
                    MissingEndpointPolicy.SKIP_EDGE -> {
                        se++
                        status = GraphIoStatus.PARTIAL
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            severity = GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.UNIFIED,
                            recordId = e.externalId,
                            message = "Missing endpoint skipped from=${e.fromExternalId} to=${e.toExternalId}",
                        )
                        continue
                    }
                }
            }
            val props = e.externalId?.let { eid ->
                options.preserveExternalIdProperty?.let { key -> e.properties + (key to eid) } ?: e.properties
            } ?: e.properties
            val created = batchWriter.addEdge(e.label, from, to, props)
            ec += created
            if (created > 0) {
                ec += batchWriter.flushEdges()
                checkpoint.edgesCommitted(vr, drainedEdges)
            }
        }

        if (status != GraphIoStatus.FAILED) {
            ec += batchWriter.flushEdges()
            checkpoint.edgesCommitted(vr, er)
            checkpoint.completed()
        } else {
            checkpoint.failed("EDGES")
        }

        return GraphImportReport(status, GraphIoFormat.GRAPHML, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
            .also { log.debug { "Suspend import completed: vertices=$vc/$vr, edges=$ec/$er, status=$status" } }
        } catch (error: CancellationException) {
            checkpoint.cancelled()
            throw error
        } finally {
            checkpoint.close()
        }
    }

    private object StopImport : RuntimeException()

    companion object : KLoggingChannel()
}

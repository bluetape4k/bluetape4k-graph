@file:Suppress("TooManyFunctions")

package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphSuspendBulkImporter
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointIdentity
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointSession
import io.bluetape4k.graph.io.jackson2.internal.Jackson2EnvelopeCodec
import io.bluetape4k.graph.io.jackson2.internal.Jackson2RecordParser
import io.bluetape4k.graph.io.jackson2.internal.NdJsonEnvelope
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
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
import io.bluetape4k.graph.io.report.GraphIoReadException
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
 * Jackson 2 기반 coroutine NDJSON bulk importer.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson2.SuspendJackson2NdJsonBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = SuspendJackson2NdJsonBulkImporter()
 * val report = importer.importGraphSuspending(
 *     source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *     operations = suspendGraphOps,
 *     options = GraphImportOptions(maxEdgeBufferSize = 50_000),
 * )
 * ```
 */
class SuspendJackson2NdJsonBulkImporter : GraphSuspendBulkImporter<GraphImportSource> {

    private val codec: Jackson2EnvelopeCodec = Jackson2EnvelopeCodec()

    override suspend fun importGraphSuspending(
        source: GraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.NDJSON_JACKSON2,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return reporter.runSuspending(
            block = { importGraphSuspending(source, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    override suspend fun importGraphSuspending(
        source: GraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        log.debug { "Starting NDJSON_JACKSON2 import (suspend): defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val checkpoint = GraphImportCheckpointSession(
            format = GraphIoFormat.NDJSON_JACKSON2,
            sourceIdentity = GraphImportCheckpointIdentity.resolve(options, source),
            options = options,
            idMap = idMap,
        )
        val batchWriter = SuspendGraphIoBatchWriter(operations, options.writeBatchSize) { boundary, error ->
            checkpoint.failed(boundary, error.message)
        }
        try {
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<GraphIoEdgeRecord>()
        val parser = Jackson2RecordParser(codec)
        var vr = 0L; var vc = 0L; var er = 0L; var ec = 0L; var sv = 0L; var se = 0L
        var status = GraphIoStatus.COMPLETED
        var failureBoundary = "VERTICES"
        var drainedEdges = checkpoint.resumeEdgesProcessed

        val coroutineContext = currentCoroutineContext()
        try {
            parser.records(source).collect { parsed ->
                coroutineContext.ensureActive()
                if (status != GraphIoStatus.FAILED) {
                    when (parsed.envelope.type) {
                        NdJsonEnvelope.TYPE_VERTEX -> {
                            vr++
                            if (checkpoint.shouldSkipVertex(vr)) return@collect
                        }
                        NdJsonEnvelope.TYPE_EDGE -> {
                            er++
                            if (checkpoint.shouldSkipEdge(er)) return@collect
                            failureBoundary = "EDGES"
                        }
                    }
                    val previousVerticesCreated = vc
                    status = importEnvelope(
                        env = parsed.envelope,
                        lineNo = parsed.lineNumber,
                        currentStatus = status,
                        options = options,
                        idMap = idMap,
                        batchWriter = batchWriter,
                        failures = failures,
                        bufferedEdges = bufferedEdges,
                        verticesCreated = { vc },
                        onVertexRead = {},
                        onVertexCreated = { created -> vc += created },
                        onVertexSkipped = { sv++ },
                        onEdgeRead = {},
                    )
                    if (vc > previousVerticesCreated) {
                        vc += batchWriter.flushVertices(idMap)
                        checkpoint.verticesCommitted(vr)
                    }
                    if (status == GraphIoStatus.FAILED) throw StopImport
                }
            }
        } catch (_: StopImport) {
            // Stop at the first terminal import failure; parser closes the source.
        } catch (error: GraphIoReadException) {
            failures += error.failure
            status = GraphIoStatus.FAILED
            failureBoundary = if (error.failure.phase == GraphIoPhase.READ_EDGE) "EDGES" else "VERTICES"
        }

        vc += batchWriter.flushVertices(idMap)
        checkpoint.verticesCommitted(vr)

        if (status == GraphIoStatus.FAILED) {
            checkpoint.failed(failureBoundary)
            log.warn { "NDJSON_JACKSON2 import (suspend) failed: vertices=$vc/$vr, edges=$ec/$er, elapsed=${watch.elapsed()}" }
            return GraphImportReport(
                status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures
            )
        }

        status = drainBufferedEdges(
            bufferedEdges = bufferedEdges,
            idMap = idMap,
            options = options,
            batchWriter = batchWriter,
            failures = failures,
            currentStatus = status,
            onEdgesCreated = { created ->
                ec += created
                if (created > 0) {
                    ec += batchWriter.flushEdges()
                    true
                } else {
                    false
                }
            },
            onEdgeSkipped = { se++ },
            onEdgeProgress = { progress ->
                drainedEdges = progress
                checkpoint.edgesCommitted(vr, drainedEdges)
            },
            startingEdgeProgress = checkpoint.resumeEdgesProcessed,
        )

        if (status != GraphIoStatus.FAILED) {
            ec += batchWriter.flushEdges()
            checkpoint.edgesCommitted(vr, er)
            checkpoint.completed()
        } else {
            checkpoint.failed("EDGES")
        }

        return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures).also {
            log.debug { "NDJSON_JACKSON2 import (suspend) completed: vertices=$vc/$vr, edges=$ec/$er, skipped=$sv/$se, status=$status, elapsed=${watch.elapsed()}" }
        }
        } catch (error: CancellationException) {
            checkpoint.cancelled()
            throw error
        } finally {
            checkpoint.close()
        }
    }

    private suspend fun drainBufferedEdges(
        bufferedEdges: ArrayDeque<GraphIoEdgeRecord>,
        idMap: GraphIoExternalIdMap,
        options: GraphImportOptions,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        currentStatus: GraphIoStatus,
        onEdgesCreated: suspend (Int) -> Boolean,
        onEdgeSkipped: () -> Unit,
        onEdgeProgress: suspend (Long) -> Unit,
        startingEdgeProgress: Long,
    ): GraphIoStatus {
        var status = currentStatus
        var processedEdges = startingEdgeProgress
        var checkpointedEdges = startingEdgeProgress
        for (edge in bufferedEdges) {
            if (status != GraphIoStatus.FAILED) {
                val result = drainBufferedEdge(
                    edge = edge,
                    idMap = idMap,
                    options = options,
                    batchWriter = batchWriter,
                    failures = failures,
                    currentStatus = status,
                    onEdgesCreated = onEdgesCreated,
                    onEdgeSkipped = onEdgeSkipped,
                )
                if (result.accepted) processedEdges++
                status = result.status
                if (status == GraphIoStatus.FAILED) {
                    if (result.flushed && processedEdges > checkpointedEdges) {
                        onEdgeProgress(processedEdges)
                        checkpointedEdges = processedEdges
                    }
                } else if (result.flushed || result.skipped) {
                    onEdgeProgress(processedEdges)
                    checkpointedEdges = processedEdges
                }
            }
        }
        return status
    }

    private suspend fun drainBufferedEdge(
        edge: GraphIoEdgeRecord,
        idMap: GraphIoExternalIdMap,
        options: GraphImportOptions,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        currentStatus: GraphIoStatus,
        onEdgesCreated: suspend (Int) -> Boolean,
        onEdgeSkipped: () -> Unit,
    ): EdgeDrainResult {
        val from = idMap.resolve(edge.fromExternalId)
        val to = idMap.resolve(edge.toExternalId)
        return if (from == null || to == null) {
            handleMissingEndpoint(edge, options, batchWriter, failures, onEdgesCreated, onEdgeSkipped)
        } else {
            val props = edge.externalId?.let { eid ->
                options.preserveExternalIdProperty?.let { key -> edge.properties + (key to eid) } ?: edge.properties
            } ?: edge.properties
            val flushed = onEdgesCreated(batchWriter.addEdge(edge.label, from, to, props))
            EdgeDrainResult(currentStatus, accepted = true, flushed = flushed)
        }
    }

    private suspend fun handleMissingEndpoint(
        edge: GraphIoEdgeRecord,
        options: GraphImportOptions,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        onEdgesCreated: suspend (Int) -> Boolean,
        onEdgeSkipped: () -> Unit,
    ): EdgeDrainResult =
        when (options.onMissingEdgeEndpoint) {
            MissingEndpointPolicy.FAIL -> {
                val flushed = onEdgesCreated(batchWriter.flushEdges())
                failures += GraphIoFailure(
                    phase = GraphIoPhase.READ_EDGE,
                    fileRole = GraphIoFileRole.UNIFIED,
                    recordId = edge.externalId,
                    message = "Unresolved endpoint from=${edge.fromExternalId} to=${edge.toExternalId}",
                )
                EdgeDrainResult(GraphIoStatus.FAILED, accepted = false, flushed = flushed)
            }
            MissingEndpointPolicy.SKIP_EDGE -> {
                onEdgeSkipped()
                failures += GraphIoFailure(
                    phase = GraphIoPhase.READ_EDGE,
                    severity = GraphIoFailureSeverity.WARN,
                    fileRole = GraphIoFileRole.UNIFIED,
                    recordId = edge.externalId,
                    message = "Missing endpoint skipped from=${edge.fromExternalId} to=${edge.toExternalId}",
                )
                EdgeDrainResult(GraphIoStatus.PARTIAL, accepted = true, skipped = true)
            }
        }

    private data class EdgeDrainResult(
        val status: GraphIoStatus,
        val accepted: Boolean,
        val flushed: Boolean = false,
        val skipped: Boolean = false,
    )

    private suspend fun importEnvelope(
        env: NdJsonEnvelope,
        lineNo: Int,
        currentStatus: GraphIoStatus,
        options: GraphImportOptions,
        idMap: GraphIoExternalIdMap,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        bufferedEdges: ArrayDeque<GraphIoEdgeRecord>,
        verticesCreated: () -> Long,
        onVertexRead: () -> Unit,
        onVertexCreated: (Int) -> Unit,
        onVertexSkipped: () -> Unit,
        onEdgeRead: () -> Unit,
    ): GraphIoStatus =
        when (env.type) {
            NdJsonEnvelope.TYPE_VERTEX -> importVertexEnvelope(
                env,
                lineNo,
                currentStatus,
                options,
                idMap,
                batchWriter,
                failures,
                onVertexRead,
                onVertexCreated,
                onVertexSkipped,
            )
            NdJsonEnvelope.TYPE_EDGE -> importEdgeEnvelope(
                env,
                lineNo,
                currentStatus,
                options,
                failures,
                bufferedEdges,
                verticesCreated,
                onEdgeRead,
            )
            else -> {
                failures += GraphIoFailure(
                    phase = GraphIoPhase.READ_VERTEX,
                    severity = GraphIoFailureSeverity.WARN,
                    fileRole = GraphIoFileRole.UNIFIED,
                    location = "line:$lineNo",
                    message = "Unknown envelope type=${env.type}",
                )
                GraphIoStatus.PARTIAL
            }
        }

    private suspend fun importVertexEnvelope(
        env: NdJsonEnvelope,
        lineNo: Int,
        currentStatus: GraphIoStatus,
        options: GraphImportOptions,
        idMap: GraphIoExternalIdMap,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        onVertexRead: () -> Unit,
        onVertexCreated: (Int) -> Unit,
        onVertexSkipped: () -> Unit,
    ): GraphIoStatus {
        val rec = toVertexRecord(env, lineNo, options, failures) ?: return GraphIoStatus.FAILED
        onVertexRead()
        val props = options.preserveExternalIdProperty
            ?.let { key -> rec.properties + (key to rec.externalId) } ?: rec.properties
        return when (idMap.putFirstOrFail(rec.externalId, GraphElementId(rec.externalId))) {
            GraphIoExternalIdMap.PutResult.CREATED -> {
                onVertexCreated(batchWriter.addVertex(rec.externalId, rec.label, props, idMap))
                currentStatus
            }
            GraphIoExternalIdMap.PutResult.SKIPPED -> {
                onVertexSkipped()
                GraphIoStatus.PARTIAL
            }
        }
    }

    private fun importEdgeEnvelope(
        env: NdJsonEnvelope,
        lineNo: Int,
        currentStatus: GraphIoStatus,
        options: GraphImportOptions,
        failures: MutableList<GraphIoFailure>,
        bufferedEdges: ArrayDeque<GraphIoEdgeRecord>,
        verticesCreated: () -> Long,
        onEdgeRead: () -> Unit,
    ): GraphIoStatus {
        val rec = toEdgeRecord(env, lineNo, options, failures) ?: return GraphIoStatus.FAILED
        onEdgeRead()
        bufferedEdges += rec
        return if (bufferedEdges.size > options.maxEdgeBufferSize) {
            val maxSize = options.maxEdgeBufferSize
            failures += GraphIoFailure(
                phase = GraphIoPhase.READ_EDGE,
                fileRole = GraphIoFileRole.UNIFIED,
                location = "line:$lineNo",
                message = "Edge buffer exceeded maxEdgeBufferSize=$maxSize; " +
                    "verticesCreated=${verticesCreated()} remain in graph as partial state",
            )
            GraphIoStatus.FAILED
        } else {
            currentStatus
        }
    }

    private fun toVertexRecord(
        env: NdJsonEnvelope,
        lineNo: Int,
        options: GraphImportOptions,
        failures: MutableList<GraphIoFailure>,
    ) = try {
        codec.toVertex(env, options.defaultVertexLabel)
    } catch (_: IllegalArgumentException) {
        failures += GraphIoFailure(
            phase = GraphIoPhase.READ_VERTEX,
            fileRole = GraphIoFileRole.UNIFIED,
            location = "line:$lineNo",
            message = if (env.id == null) "Invalid vertex envelope: missing id" else "Invalid vertex envelope",
        )
        null
    }

    private fun toEdgeRecord(
        env: NdJsonEnvelope,
        lineNo: Int,
        options: GraphImportOptions,
        failures: MutableList<GraphIoFailure>,
    ) = try {
        codec.toEdge(env, options.defaultEdgeLabel)
    } catch (_: IllegalArgumentException) {
        failures += GraphIoFailure(
            phase = GraphIoPhase.READ_EDGE,
            fileRole = GraphIoFileRole.UNIFIED,
            location = "line:$lineNo",
            message = "Invalid edge envelope",
        )
        null
    }

    private object StopImport : RuntimeException()

    companion object : KLoggingChannel()
}

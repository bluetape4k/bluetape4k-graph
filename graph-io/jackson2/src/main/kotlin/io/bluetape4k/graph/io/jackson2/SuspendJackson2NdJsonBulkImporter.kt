package io.bluetape4k.graph.io.jackson2

import com.fasterxml.jackson.core.JsonProcessingException
import io.bluetape4k.graph.io.contract.GraphSuspendBulkImporter
import io.bluetape4k.graph.io.jackson2.internal.Jackson2EnvelopeCodec
import io.bluetape4k.graph.io.jackson2.internal.NdJsonEnvelope
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoPhase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Coroutine NDJSON bulk importer backed by Jackson 2.
 *
 * Example:
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
    ): GraphImportReport {
        log.debug { "Starting NDJSON_JACKSON2 import (suspend): defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val batchWriter = SuspendGraphIoBatchWriter(operations, options.batchSize)
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<GraphIoEdgeRecord>()
        var vr = 0L; var vc = 0L; var er = 0L; var ec = 0L; var sv = 0L; var se = 0L
        var status = GraphIoStatus.COMPLETED

        val coroutineContext = currentCoroutineContext()
        val reader = withContext(Dispatchers.IO) { GraphIoPaths.openReader(source) }
        try {
            var lineNo = 0
            var keepReading = true
            while (keepReading && status != GraphIoStatus.FAILED) {
                coroutineContext.ensureActive()
                val raw = withContext(Dispatchers.IO) { reader.readLine() }
                if (raw == null) {
                    keepReading = false
                } else {
                    lineNo++
                    status = importLine(
                        raw = raw,
                        lineNo = lineNo,
                        currentStatus = status,
                        options = options,
                        idMap = idMap,
                        batchWriter = batchWriter,
                        failures = failures,
                        bufferedEdges = bufferedEdges,
                        verticesCreated = { vc },
                        onVertexRead = { vr++ },
                        onVertexCreated = { created -> vc += created },
                        onVertexSkipped = { sv++ },
                        onEdgeRead = { er++ },
                    )
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { reader.close() }
        }

        vc += batchWriter.flushVertices(idMap)

        if (status == GraphIoStatus.FAILED) {
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
            onEdgesCreated = { created -> ec += created },
            onEdgeSkipped = { se++ },
        )

        if (status != GraphIoStatus.FAILED) {
            ec += batchWriter.flushEdges()
        }

        return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures).also {
            log.debug { "NDJSON_JACKSON2 import (suspend) completed: vertices=$vc/$vr, edges=$ec/$er, skipped=$sv/$se, status=$status, elapsed=${watch.elapsed()}" }
        }
    }

    private suspend fun importLine(
        raw: String,
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
    ): GraphIoStatus {
        var nextStatus = currentStatus
        val line = raw.trim()
        if (line.isNotBlank()) {
            val env = parseEnvelope(line, lineNo, failures)
            nextStatus = env?.let {
                importEnvelope(
                    env = it,
                    lineNo = lineNo,
                    currentStatus = currentStatus,
                    options = options,
                    idMap = idMap,
                    batchWriter = batchWriter,
                    failures = failures,
                    bufferedEdges = bufferedEdges,
                    verticesCreated = verticesCreated,
                    onVertexRead = onVertexRead,
                    onVertexCreated = onVertexCreated,
                    onVertexSkipped = onVertexSkipped,
                    onEdgeRead = onEdgeRead,
                )
            } ?: GraphIoStatus.FAILED
        }
        return nextStatus
    }

    private suspend fun drainBufferedEdges(
        bufferedEdges: ArrayDeque<GraphIoEdgeRecord>,
        idMap: GraphIoExternalIdMap,
        options: GraphImportOptions,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        currentStatus: GraphIoStatus,
        onEdgesCreated: (Int) -> Unit,
        onEdgeSkipped: () -> Unit,
    ): GraphIoStatus {
        var status = currentStatus
        for (edge in bufferedEdges) {
            if (status != GraphIoStatus.FAILED) {
                status = drainBufferedEdge(
                    edge = edge,
                    idMap = idMap,
                    options = options,
                    batchWriter = batchWriter,
                    failures = failures,
                    currentStatus = status,
                    onEdgesCreated = onEdgesCreated,
                    onEdgeSkipped = onEdgeSkipped,
                )
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
        onEdgesCreated: (Int) -> Unit,
        onEdgeSkipped: () -> Unit,
    ): GraphIoStatus {
        val from = idMap.resolve(edge.fromExternalId)
        val to = idMap.resolve(edge.toExternalId)
        return if (from == null || to == null) {
            handleMissingEndpoint(edge, options, batchWriter, failures, onEdgesCreated, onEdgeSkipped)
        } else {
            val props = edge.externalId?.let { eid ->
                options.preserveExternalIdProperty?.let { key -> edge.properties + (key to eid) } ?: edge.properties
            } ?: edge.properties
            onEdgesCreated(batchWriter.addEdge(edge.label, from, to, props))
            currentStatus
        }
    }

    private suspend fun handleMissingEndpoint(
        edge: GraphIoEdgeRecord,
        options: GraphImportOptions,
        batchWriter: SuspendGraphIoBatchWriter,
        failures: MutableList<GraphIoFailure>,
        onEdgesCreated: (Int) -> Unit,
        onEdgeSkipped: () -> Unit,
    ): GraphIoStatus =
        when (options.onMissingEdgeEndpoint) {
            MissingEndpointPolicy.FAIL -> {
                onEdgesCreated(batchWriter.flushEdges())
                failures += GraphIoFailure(
                    phase = GraphIoPhase.READ_EDGE,
                    fileRole = GraphIoFileRole.UNIFIED,
                    recordId = edge.externalId,
                    message = "Unresolved endpoint from=${edge.fromExternalId} to=${edge.toExternalId}",
                )
                GraphIoStatus.FAILED
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
                GraphIoStatus.PARTIAL
            }
        }

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

    private fun parseEnvelope(
        line: String,
        lineNo: Int,
        failures: MutableList<GraphIoFailure>,
    ): NdJsonEnvelope? =
        try {
            codec.parseLine(line)
        } catch (e: JsonProcessingException) {
            log.warn(e) { "Malformed JSON at line $lineNo: ${e.message}" }
            failures += GraphIoFailure(
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.UNIFIED,
                location = "line:$lineNo",
                message = "Malformed JSON: ${e.message}",
            )
            null
        }

    private fun toVertexRecord(
        env: NdJsonEnvelope,
        lineNo: Int,
        options: GraphImportOptions,
        failures: MutableList<GraphIoFailure>,
    ) = try {
        codec.toVertex(env, options.defaultVertexLabel)
    } catch (e: IllegalArgumentException) {
        failures += GraphIoFailure(
            phase = GraphIoPhase.READ_VERTEX,
            fileRole = GraphIoFileRole.UNIFIED,
            location = "line:$lineNo",
            message = "Invalid vertex envelope: ${e.message}",
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
    } catch (e: IllegalArgumentException) {
        failures += GraphIoFailure(
            phase = GraphIoPhase.READ_EDGE,
            fileRole = GraphIoFileRole.UNIFIED,
            location = "line:$lineNo",
            message = "Invalid edge envelope: ${e.message}",
        )
        null
    }

    companion object : KLoggingChannel()
}

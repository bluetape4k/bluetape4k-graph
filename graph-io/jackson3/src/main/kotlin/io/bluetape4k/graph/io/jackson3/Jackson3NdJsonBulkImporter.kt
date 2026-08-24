package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointIdentity
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointSession
import io.bluetape4k.graph.io.jackson3.internal.Jackson3EnvelopeCodec
import io.bluetape4k.graph.io.jackson3.internal.Jackson3RecordParser
import io.bluetape4k.graph.io.jackson3.internal.NdJsonEnvelope
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
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.GraphIoBatchWriter
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn

/**
 * Jackson 3 기반 blocking NDJSON bulk importer.
 *
 * importer는 단일 file에서 vertex와 edge envelope를 감지하고 edge를 buffer한 뒤,
 * vertex 생성 이후 edge를 쓴다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = Jackson3NdJsonBulkImporter()
 * val report = importer.importGraph(
 *     source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphImportOptions(defaultVertexLabel = "Entity"),
 * )
 * ```
 */
class Jackson3NdJsonBulkImporter : GraphBulkImporter<GraphImportSource> {

    private val codec: Jackson3EnvelopeCodec = Jackson3EnvelopeCodec()

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount")
    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.NDJSON_JACKSON3,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return reporter.run(
            block = { importGraph(source, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount")
    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        log.debug { "Starting NDJSON_JACKSON3 import: defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val checkpoint = GraphImportCheckpointSession(
            format = GraphIoFormat.NDJSON_JACKSON3,
            sourceIdentity = GraphImportCheckpointIdentity.resolve(options, source),
            options = options,
            idMap = idMap,
        )
        val batchWriter = GraphIoBatchWriter(operations, options.writeBatchSize) { boundary, error ->
            checkpoint.failed(boundary, error.message)
        }
        try {
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<GraphIoEdgeRecord>()
        val parser = Jackson3RecordParser(codec)
        var vr = 0L; var vc = 0L; var er = 0L; var ec = 0L; var sv = 0L; var se = 0L
        var status = GraphIoStatus.COMPLETED
        var failureBoundary = "VERTICES"
        var drainedEdges = checkpoint.resumeEdgesProcessed

        try {
            parser.parse(
                source = source,
                onRecord = onRecord@{ parsed ->
                    if (status == GraphIoStatus.FAILED) throw StopImport
                    val lineNo = parsed.lineNumber
                    val env = parsed.envelope
                    when (env.type) {
                        NdJsonEnvelope.TYPE_VERTEX -> {
                            vr++
                            if (checkpoint.shouldSkipVertex(vr)) return@onRecord
                            val rec = try {
                                codec.toVertex(env, options.defaultVertexLabel)
                            } catch (_: IllegalArgumentException) {
                                failureBoundary = "VERTICES"
                                failures += invalidVertexFailure(env, lineNo)
                                status = GraphIoStatus.FAILED
                                throw StopImport
                            }
                            val props = options.preserveExternalIdProperty
                                ?.let { rec.properties + (it to rec.externalId) } ?: rec.properties
                            when (idMap.putFirstOrFail(rec.externalId, GraphElementId(rec.externalId))) {
                                GraphIoExternalIdMap.PutResult.CREATED -> {
                                    val created = batchWriter.addVertex(rec.externalId, rec.label, props, idMap)
                                    vc += created
                                    if (created > 0) {
                                        vc += batchWriter.flushVertices(idMap)
                                        checkpoint.verticesCommitted(vr)
                                    }
                                }
                                GraphIoExternalIdMap.PutResult.SKIPPED -> {
                                    sv++
                                    status = GraphIoStatus.PARTIAL
                                }
                            }
                        }
                        NdJsonEnvelope.TYPE_EDGE -> {
                            er++
                            if (checkpoint.shouldSkipEdge(er)) return@onRecord
                            failureBoundary = "EDGES"
                            val edge = try {
                                codec.toEdge(env, options.defaultEdgeLabel)
                            } catch (_: IllegalArgumentException) {
                                failures += invalidEdgeFailure(lineNo)
                                status = GraphIoStatus.FAILED
                                throw StopImport
                            }
                            bufferedEdges += edge
                            if (bufferedEdges.size > options.maxEdgeBufferSize) {
                                failures += GraphIoFailure(
                                    phase = GraphIoPhase.READ_EDGE,
                                    fileRole = GraphIoFileRole.UNIFIED,
                                    location = "line:$lineNo",
                                    message = "Edge buffer exceeded maxEdgeBufferSize=${options.maxEdgeBufferSize}; " +
                                        "verticesCreated=$vc remain in graph as partial state",
                                )
                                status = GraphIoStatus.FAILED
                                throw StopImport
                            }
                        }
                        else -> failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_VERTEX,
                            severity = GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.UNIFIED,
                            location = "line:$lineNo",
                            message = "Unknown envelope type=${env.type}",
                        )
                    }
                },
                onFailure = { failure ->
                    failures += failure
                    status = GraphIoStatus.FAILED
                },
            )
        } catch (_: StopImport) {
            // Stop parsing at the first terminal import failure and close source.
        }

        vc += batchWriter.flushVertices(idMap)
        checkpoint.verticesCommitted(vr)

        if (status == GraphIoStatus.FAILED) {
            checkpoint.failed(failureBoundary)
            log.warn { "NDJSON_JACKSON3 import failed: vertices=$vc/$vr, edges=$ec/$er, elapsed=${watch.elapsed()}" }
            return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON3, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
        }

        // 버퍼된 edges flush
        for (e in bufferedEdges) {
            drainedEdges++
            val from = idMap.resolve(e.fromExternalId)
            val to = idMap.resolve(e.toExternalId)
            if (from == null || to == null) {
                when (options.onMissingEdgeEndpoint) {
                    MissingEndpointPolicy.FAIL -> {
                        ec += batchWriter.flushEdges()
                        checkpoint.edgesCommitted(vr, drainedEdges - 1)
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            fileRole = GraphIoFileRole.UNIFIED,
                            recordId = e.externalId,
                            message = "Unresolved endpoint from=${e.fromExternalId} to=${e.toExternalId}",
                        )
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

        return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON3, vr, vc, er, ec, sv, se, watch.elapsed(), failures).also {
            log.debug { "NDJSON_JACKSON3 import completed: vertices=$vc/$vr, edges=$ec/$er, skipped=$sv/$se, status=$status, elapsed=${watch.elapsed()}" }
        }
        } finally {
            checkpoint.close()
        }
    }

    private fun invalidVertexFailure(env: NdJsonEnvelope, lineNumber: Int): GraphIoFailure = GraphIoFailure(
        phase = GraphIoPhase.READ_VERTEX,
        fileRole = GraphIoFileRole.UNIFIED,
        location = "line:$lineNumber",
        message = if (env.id == null) "Invalid vertex envelope: missing id" else "Invalid vertex envelope",
    )

    private fun invalidEdgeFailure(lineNumber: Int): GraphIoFailure = GraphIoFailure(
        phase = GraphIoPhase.READ_EDGE,
        fileRole = GraphIoFileRole.UNIFIED,
        location = "line:$lineNumber",
        message = "Invalid edge envelope",
    )

    private object StopImport : RuntimeException()

    companion object : KLogging()
}

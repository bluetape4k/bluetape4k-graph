package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.contract.GraphBulkImporter
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
 * Jackson 2 기반 blocking NDJSON bulk importer.
 *
 * importer는 단일 file에서 vertex와 edge envelope를 감지하고 edge를 buffer한 뒤,
 * vertex 생성 이후 edge를 쓴다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = Jackson2NdJsonBulkImporter()
 * val report = importer.importGraph(
 *     source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphImportOptions(batchSize = 1_000),
 * )
 * ```
 */
class Jackson2NdJsonBulkImporter : GraphBulkImporter<GraphImportSource> {

    private val codec: Jackson2EnvelopeCodec = Jackson2EnvelopeCodec()

    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        log.debug { "Starting NDJSON_JACKSON2 import: defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val batchWriter = GraphIoBatchWriter(operations, options.batchSize)
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<GraphIoEdgeRecord>()
        var vr = 0L; var vc = 0L; var er = 0L; var ec = 0L; var sv = 0L; var se = 0L
        var status = GraphIoStatus.COMPLETED

        GraphIoPaths.openReader(source).use { reader ->
            var lineNo = 0
            reader.forEachLine { raw ->
                if (status == GraphIoStatus.FAILED) return@forEachLine
                lineNo++
                val line = raw.trim().ifBlank { return@forEachLine }
                val env = runCatching { codec.parseLine(line) }.getOrElse { e ->
                    log.warn(e) { "Malformed JSON at line $lineNo: ${e.message}" }
                    failures += GraphIoFailure(
                        phase = GraphIoPhase.READ_VERTEX,
                        fileRole = GraphIoFileRole.UNIFIED,
                        location = "line:$lineNo",
                        message = "Malformed JSON: ${e.message}",
                    )
                    status = GraphIoStatus.FAILED
                    return@forEachLine
                }
                when (env.type) {
                    NdJsonEnvelope.TYPE_VERTEX -> {
                        vr++
                        val rec = codec.toVertex(env, options.defaultVertexLabel)
                        val props = options.preserveExternalIdProperty
                            ?.let { rec.properties + (it to rec.externalId) } ?: rec.properties
                        when (idMap.putFirstOrFail(rec.externalId, GraphElementId(rec.externalId))) {
                            GraphIoExternalIdMap.PutResult.CREATED -> {
                                vc += batchWriter.addVertex(rec.externalId, rec.label, props, idMap)
                            }
                            GraphIoExternalIdMap.PutResult.SKIPPED -> {
                                sv++
                                status = GraphIoStatus.PARTIAL
                            }
                        }
                    }
                    NdJsonEnvelope.TYPE_EDGE -> {
                        er++
                        bufferedEdges += codec.toEdge(env, options.defaultEdgeLabel)
                        if (bufferedEdges.size > options.maxEdgeBufferSize) {
                            failures += GraphIoFailure(
                                phase = GraphIoPhase.READ_EDGE,
                                fileRole = GraphIoFileRole.UNIFIED,
                                location = "line:$lineNo",
                                message = "Edge buffer exceeded maxEdgeBufferSize=${options.maxEdgeBufferSize}; " +
                                    "verticesCreated=$vc remain in graph as partial state",
                            )
                            status = GraphIoStatus.FAILED
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
            }
        }

        vc += batchWriter.flushVertices(idMap)

        if (status == GraphIoStatus.FAILED) {
            log.warn { "NDJSON_JACKSON2 import failed: vertices=$vc/$vr, edges=$ec/$er, elapsed=${watch.elapsed()}" }
            return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
        }

        // 버퍼된 edges flush
        for (e in bufferedEdges) {
            val from = idMap.resolve(e.fromExternalId)
            val to = idMap.resolve(e.toExternalId)
            if (from == null || to == null) {
                when (options.onMissingEdgeEndpoint) {
                    MissingEndpointPolicy.FAIL -> {
                        ec += batchWriter.flushEdges()
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
            ec += batchWriter.addEdge(e.label, from, to, props)
        }

        if (status != GraphIoStatus.FAILED) {
            ec += batchWriter.flushEdges()
        }

        return GraphImportReport(status, GraphIoFormat.NDJSON_JACKSON2, vr, vc, er, ec, sv, se, watch.elapsed(), failures).also {
            log.debug { "NDJSON_JACKSON2 import completed: vertices=$vc/$vr, edges=$ec/$er, skipped=$sv/$se, status=$status, elapsed=${watch.elapsed()}" }
        }
    }

    companion object : KLogging()
}

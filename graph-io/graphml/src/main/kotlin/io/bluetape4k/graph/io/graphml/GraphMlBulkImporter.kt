package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.graphml.internal.StaxGraphMlReader
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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn

/**
 * GraphML 동기 벌크 임포터.
 * StAX 파서로 전체 XML을 읽어 정점을 먼저 생성한 뒤 간선을 연결한다.
 */
class GraphMlBulkImporter : GraphBulkImporter<GraphImportSource> {

    private val reader = StaxGraphMlReader()

    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, operations, options, GraphMlImportOptions())

    fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): GraphImportReport {
        log.debug { "Starting GRAPHML import: defaultVertexLabel=${graphMlOptions.defaultVertexLabel}" }
        val watch = GraphIoStopwatch()

        val parsed = GraphIoPaths.openInputStream(source).use { reader.read(it, graphMlOptions) }

        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val failures = mutableListOf<GraphIoFailure>()
        failures.addAll(parsed.failures)
        var vr = parsed.vertices.size.toLong()
        var vc = 0L
        var er = parsed.edges.size.toLong()
        var ec = 0L
        var sv = 0L
        var se = 0L
        var status = GraphIoStatus.COMPLETED

        for (v in parsed.vertices) {
            val props = options.preserveExternalIdProperty
                ?.let { v.properties + (it to v.externalId) } ?: v.properties
            val created = operations.createVertex(v.label, props)
            when (idMap.putFirstOrFail(v.externalId, created.id)) {
                GraphIoExternalIdMap.PutResult.CREATED -> vc++
                GraphIoExternalIdMap.PutResult.SKIPPED -> {
                    sv++
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

        for (e in parsed.edges) {
            val from = idMap.resolve(e.fromExternalId)
            val to = idMap.resolve(e.toExternalId)
            if (from == null || to == null) {
                when (options.onMissingEdgeEndpoint) {
                    MissingEndpointPolicy.FAIL -> {
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
            operations.createEdge(from, to, e.label, props)
            ec++
        }

        return GraphImportReport(status, GraphIoFormat.GRAPHML, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
            .also { log.debug { "Import completed: vertices=$vc/$vr, edges=$ec/$er, skipped=$sv/$se, status=$status, elapsed=${watch.elapsed()}" } }
    }

    companion object : KLogging()
}

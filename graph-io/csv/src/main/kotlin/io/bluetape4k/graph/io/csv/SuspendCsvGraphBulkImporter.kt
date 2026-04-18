package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.coroutines.SuspendCsvRecordReader
import io.bluetape4k.graph.io.contract.GraphSuspendBulkImporter
import io.bluetape4k.graph.io.csv.internal.CsvRecordCodec
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * CSV 코루틴(suspend) 벌크 임포터.
 * SuspendCsvRecordReader로 정점과 엣지를 Flow로 스트리밍하여 처리한다.
 */
class SuspendCsvGraphBulkImporter : GraphSuspendBulkImporter<CsvGraphImportSource> {

    override suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraphSuspending(source, operations, options, CsvGraphIoOptions())

    suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport = withContext(Dispatchers.IO) {
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val failures = mutableListOf<GraphIoFailure>()
        var verticesRead = 0L
        var verticesCreated = 0L
        var skippedVertices = 0L
        var edgesRead = 0L
        var edgesCreated = 0L
        var skippedEdges = 0L
        var status = GraphIoStatus.COMPLETED

        // --- 정점 패스 ---
        SuspendCsvRecordReader().read(
            GraphIoPaths.openInputStream(source.vertices),
            skipHeaders = true,
        ) { it }.collect { record ->
            if (status == GraphIoStatus.FAILED) return@collect
            verticesRead++
            val externalId = record.getString("id").orEmpty()
            val label = record.getString("label").orEmpty().ifBlank { options.defaultVertexLabel }
            if (externalId.isBlank()) {
                failures += GraphIoFailure(
                    phase = GraphIoPhase.READ_VERTEX,
                    fileRole = GraphIoFileRole.VERTICES,
                    message = "Blank vertex id at row $verticesRead"
                )
                status = GraphIoStatus.FAILED
                return@collect
            }
            val putResult = idMap.putFirstOrFail(externalId, GraphElementId(externalId))
            if (putResult == GraphIoExternalIdMap.PutResult.SKIPPED) {
                skippedVertices++
                status = GraphIoStatus.PARTIAL
                failures += GraphIoFailure(
                    phase = GraphIoPhase.CREATE_VERTEX,
                    severity = GraphIoFailureSeverity.WARN,
                    fileRole = GraphIoFileRole.VERTICES,
                    recordId = externalId,
                    message = "Duplicate vertex externalId skipped: $externalId"
                )
                return@collect
            }
            val rowMap = record.toFieldMap()
            val props = buildMap<String, Any?> {
                putAll(codec.extractProperties(rowMap))
                options.preserveExternalIdProperty?.let { key -> put(key, externalId) }
            }
            val created = operations.createVertex(label, props)
            idMap.put(externalId, created.id)
            verticesCreated++
        }

        if (status == GraphIoStatus.FAILED) {
            return@withContext buildReport(
                watch, failures, GraphIoStatus.FAILED,
                verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
            )
        }

        // --- 엣지 패스 ---
        SuspendCsvRecordReader().read(
            GraphIoPaths.openInputStream(source.edges),
            skipHeaders = true,
        ) { it }.collect { record ->
            if (status == GraphIoStatus.FAILED) return@collect
            edgesRead++
            val label = record.getString("label").orEmpty().ifBlank { options.defaultEdgeLabel }
            val from = record.getString("from").orEmpty()
            val to = record.getString("to").orEmpty()
            val fromId = idMap.resolve(from)
            val toId = idMap.resolve(to)
            if (fromId == null || toId == null) {
                when (options.onMissingEdgeEndpoint) {
                    MissingEndpointPolicy.FAIL -> {
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            fileRole = GraphIoFileRole.EDGES,
                            message = "Unresolved endpoint from=$from to=$to"
                        )
                        status = GraphIoStatus.FAILED
                        return@collect
                    }
                    MissingEndpointPolicy.SKIP_EDGE -> {
                        skippedEdges++
                        status = GraphIoStatus.PARTIAL
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            severity = GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.EDGES,
                            message = "Missing endpoint skipped from=$from to=$to"
                        )
                        return@collect
                    }
                }
            }
            val rowMap = record.toFieldMap()
            val props = buildMap<String, Any?> {
                putAll(codec.extractProperties(rowMap))
                val externalEdgeId = record.getString("id")?.takeIf { it.isNotBlank() }
                externalEdgeId?.let { eid ->
                    options.preserveExternalIdProperty?.let { key -> put(key, eid) }
                }
            }
            operations.createEdge(fromId ?: return@collect, toId ?: return@collect, label, props)
            edgesCreated++
        }

        buildReport(
            watch, failures, status,
            verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
        )
    }

    private fun buildReport(
        watch: GraphIoStopwatch,
        failures: List<GraphIoFailure>,
        status: GraphIoStatus,
        vr: Long,
        vc: Long,
        er: Long,
        ec: Long,
        sv: Long,
        se: Long,
    ) = GraphImportReport(status, GraphIoFormat.CSV, vr, vc, er, ec, sv, se, watch.elapsed(), failures)

    companion object : KLoggingChannel()
}

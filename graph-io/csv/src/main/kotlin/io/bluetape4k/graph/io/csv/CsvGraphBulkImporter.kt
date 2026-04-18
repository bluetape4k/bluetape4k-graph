package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.CsvRecordReader
import io.bluetape4k.graph.io.contract.GraphBulkImporter
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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn

/**
 * CSV 동기 벌크 임포터.
 * 정점 파일을 모두 읽어 외부ID-백엔드ID 맵을 구축한 뒤, 엣지 파일을 처리한다.
 */
class CsvGraphBulkImporter : GraphBulkImporter<CsvGraphImportSource> {

    override fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, operations, options, CsvGraphIoOptions())

    fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport {
        log.debug { "Starting CSV import: defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
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
        val vertexRecords = CsvRecordReader().read(
            GraphIoPaths.openInputStream(source.vertices),
            skipHeaders = true,
        ) { it }

        for (record in vertexRecords) {
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
                break
            }
            val putResult = idMap.putFirstOrFail(
                externalId,
                io.bluetape4k.graph.model.GraphElementId(externalId)
            )
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
                continue
            }
            val rowMap: Map<String, String?> = record.toFieldMap()
            val props = buildMap<String, Any?> {
                putAll(codec.extractProperties(rowMap))
                options.preserveExternalIdProperty?.let { key -> put(key, externalId) }
            }
            val created = operations.createVertex(label, props)
            idMap.put(externalId, created.id)
            verticesCreated++
        }

        if (status == GraphIoStatus.FAILED) {
            log.warn { "CSV import failed during vertex pass: vertices=$verticesCreated/$verticesRead, elapsed=${watch.elapsed()}" }
            return buildReport(
                watch, failures, GraphIoStatus.FAILED,
                verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
            )
        }

        // --- 엣지 패스 ---
        val edgeRecords = CsvRecordReader().read(
            GraphIoPaths.openInputStream(source.edges),
            skipHeaders = true,
        ) { it }

        for (record in edgeRecords) {
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
                        break
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
                        continue
                    }
                }
            }
            val rowMap: Map<String, String?> = record.toFieldMap()
            val props = buildMap<String, Any?> {
                putAll(codec.extractProperties(rowMap))
                val externalEdgeId = record.getString("id")?.takeIf { it.isNotBlank() }
                externalEdgeId?.let { eid ->
                    options.preserveExternalIdProperty?.let { key -> put(key, eid) }
                }
            }
            operations.createEdge(fromId ?: continue, toId ?: continue, label, props)
            edgesCreated++
        }

        return buildReport(
            watch, failures, status,
            verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
        ).also {
            log.debug { "CSV import completed: vertices=$verticesCreated/$verticesRead, edges=$edgesCreated/$edgesRead, skipped=$skippedVertices/$skippedEdges, status=$status, elapsed=${watch.elapsed()}" }
        }
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

    companion object : KLogging()
}

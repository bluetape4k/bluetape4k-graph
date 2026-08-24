package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointIdentity
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointSession
import io.bluetape4k.graph.io.csv.internal.CsvRecordCodec
import io.bluetape4k.graph.io.csv.internal.CsvRecordParser
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphImportReport
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
 * CSV 동기 벌크 임포터.
 *
 * 정점 CSV 파일을 전부 읽어 외부ID→백엔드ID 맵을 구축한 뒤, 간선 CSV 파일을 처리하는 2-패스 방식으로 동작한다.
 * 중복 정점 ID 및 누락된 간선 끝점 처리 정책은 [GraphImportOptions]로 제어한다.
 *
 * ```kotlin
 * val importer = CsvGraphBulkImporter()
 * val source = CsvGraphImportSource(
 *     vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
 *     edges    = GraphImportSource.PathSource(Paths.get("edges.csv")),
 * )
 * val options = GraphImportOptions(
 *     onDuplicateVertexId   = DuplicateVertexPolicy.SKIP,
 *     onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE,
 * )
 * val report = importer.importGraph(source, graphOps, options)
 * println("imported ${report.verticesCreated} vertices, ${report.edgesCreated} edges — ${report.status}")
 * ```
 */
class CsvGraphBulkImporter : GraphBulkImporter<CsvGraphImportSource> {

    override fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, operations, options, CsvGraphIoOptions())

    override fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport = importGraph(source, operations, options, CsvGraphIoOptions(), listener)

    fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = io.bluetape4k.graph.io.report.GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(source.vertices),
                    GraphIoPaths.sizeOf(source.edges),
                )
            },
        )
        return reporter.run(
            block = { importGraph(source, operations, options, csvOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport {
        log.debug { "Starting CSV import: defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val parser = CsvRecordParser()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val checkpoint = GraphImportCheckpointSession(
            format = GraphIoFormat.CSV,
            sourceIdentity = GraphImportCheckpointIdentity.resolve(options, source.vertices, source.edges),
            options = options,
            idMap = idMap,
            importOptionsIdentity = GraphImportCheckpointIdentity.optionsIdentity(
                options,
                csvOptions.propertyMode.toString(),
            ),
        )
        val batchWriter = GraphIoBatchWriter(operations, options.writeBatchSize) { boundary, error ->
            checkpoint.failed(boundary, error.message)
        }
        try {
        val failures = mutableListOf<GraphIoFailure>()
        var verticesRead = 0L
        var verticesCreated = 0L
        var skippedVertices = 0L
        var edgesRead = 0L
        var edgesCreated = 0L
        var skippedEdges = 0L
        var status = GraphIoStatus.COMPLETED

        // --- 정점 패스 ---
        var stopVertexPass = false
        parser.parseVertices(
            source = source.vertices,
            onRecord = onRecord@{ record ->
                if (!stopVertexPass) {
                    verticesRead++
                    if (checkpoint.shouldSkipVertex(verticesRead)) return@onRecord
                    val externalId = record.getString("id").orEmpty()
                    val label = record.getString("label").orEmpty().ifBlank { options.defaultVertexLabel }
                    if (externalId.isBlank()) {
                        verticesCreated += batchWriter.flushVertices(idMap)
                        checkpoint.verticesCommitted(verticesRead - 1)
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_VERTEX,
                            fileRole = GraphIoFileRole.VERTICES,
                            location = "row:${record.rowNumber}",
                            message = "Blank vertex id"
                        )
                        status = GraphIoStatus.FAILED
                        stopVertexPass = true
                    } else {
                        val putResult = idMap.putFirstOrFail(
                            externalId,
                            GraphElementId(externalId)
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
                        } else {
                            val rowMap: Map<String, String?> = record.toColumnMap()
                            val props = buildMap<String, Any?> {
                                putAll(codec.extractProperties(rowMap))
                                options.preserveExternalIdProperty?.let { key -> put(key, externalId) }
                            }
                            val created = batchWriter.addVertex(externalId, label, props, idMap)
                            verticesCreated += created
                            if (created > 0) {
                                verticesCreated += batchWriter.flushVertices(idMap)
                                checkpoint.verticesCommitted(verticesRead)
                            }
                        }
                    }
                }
            },
            onFailure = { failure ->
                verticesCreated += batchWriter.flushVertices(idMap)
                failures += failure
                status = GraphIoStatus.FAILED
                stopVertexPass = true
                checkpoint.verticesCommitted(verticesRead)
                checkpoint.failed("VERTICES", failure.message)
            },
        )

        if (status == GraphIoStatus.FAILED) {
            checkpoint.failed("VERTICES")
            log.warn { "CSV import failed during vertex pass: vertices=$verticesCreated/$verticesRead, elapsed=${watch.elapsed()}" }
            return buildReport(
                watch, failures, GraphIoStatus.FAILED,
                verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
            )
        }

        verticesCreated += batchWriter.flushVertices(idMap)
        checkpoint.verticesCommitted(verticesRead)

        // --- 엣지 패스 ---
        var stopEdgePass = false
        parser.parseEdges(
            source = source.edges,
            onRecord = onRecord@{ record ->
                if (!stopEdgePass) {
                    edgesRead++
                    if (checkpoint.shouldSkipEdge(edgesRead)) return@onRecord
                    val label = record.getString("label").orEmpty().ifBlank { options.defaultEdgeLabel }
                    val from = record.getString("from").orEmpty()
                    val to = record.getString("to").orEmpty()
                    val fromId = idMap.resolve(from)
                    val toId = idMap.resolve(to)
                    var skipCurrentEdge = false
                    if (fromId == null || toId == null) {
                        when (options.onMissingEdgeEndpoint) {
                            MissingEndpointPolicy.FAIL -> {
                                edgesCreated += batchWriter.flushEdges()
                                checkpoint.edgesCommitted(verticesRead, edgesRead - 1)
                                failures += GraphIoFailure(
                                    phase = GraphIoPhase.READ_EDGE,
                                    fileRole = GraphIoFileRole.EDGES,
                                    location = "row:${record.rowNumber}",
                                    message = "Unresolved edge endpoint"
                                )
                                status = GraphIoStatus.FAILED
                                stopEdgePass = true
                            }
                            MissingEndpointPolicy.SKIP_EDGE -> {
                                skippedEdges++
                                status = GraphIoStatus.PARTIAL
                                failures += GraphIoFailure(
                                    phase = GraphIoPhase.READ_EDGE,
                                    severity = GraphIoFailureSeverity.WARN,
                                    fileRole = GraphIoFileRole.EDGES,
                                    location = "row:${record.rowNumber}",
                                    message = "Missing endpoint skipped"
                                )
                                skipCurrentEdge = true
                            }
                        }
                    }
                    if (!stopEdgePass && !skipCurrentEdge) {
                        val resolvedFrom = requireNotNull(fromId)
                        val resolvedTo = requireNotNull(toId)
                        val rowMap: Map<String, String?> = record.toColumnMap()
                        val props = buildMap<String, Any?> {
                            putAll(codec.extractProperties(rowMap))
                            val externalEdgeId = record.getString("id")?.takeIf { it.isNotBlank() }
                            externalEdgeId?.let { eid ->
                                options.preserveExternalIdProperty?.let { key -> put(key, eid) }
                            }
                        }
                        val created = batchWriter.addEdge(label, resolvedFrom, resolvedTo, props)
                        edgesCreated += created
                        if (created > 0) {
                            edgesCreated += batchWriter.flushEdges()
                            checkpoint.edgesCommitted(verticesRead, edgesRead)
                        }
                    }
                }
            },
            onFailure = { failure ->
                edgesCreated += batchWriter.flushEdges()
                checkpoint.edgesCommitted(verticesRead, edgesRead)
                failures += failure
                status = GraphIoStatus.FAILED
                stopEdgePass = true
                checkpoint.failed("EDGES", failure.message)
            },
        )

        if (status != GraphIoStatus.FAILED) {
            edgesCreated += batchWriter.flushEdges()
            checkpoint.edgesCommitted(verticesRead, edgesRead)
            checkpoint.completed()
        } else {
            checkpoint.failed("EDGES")
        }

        return buildReport(
            watch, failures, status,
            verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
        ).also {
            log.debug { "CSV import completed: vertices=$verticesCreated/$verticesRead, edges=$edgesCreated/$edgesRead, skipped=$skippedVertices/$skippedEdges, status=$status, elapsed=${watch.elapsed()}" }
        }
        } finally {
            checkpoint.close()
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
    ) = GraphImportReport(status, GraphIoFormat.CSV, vr, vc, er, ec, sv, se, watch.elapsed(), failures.toList())

    companion object : KLogging()
}

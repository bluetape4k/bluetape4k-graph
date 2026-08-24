package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.contract.GraphSuspendBulkImporter
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
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.io.support.SuspendGraphIoBatchWriter
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import kotlinx.coroutines.flow.collect

/**
 * Coroutine bulk importer for CSV graph data.
 *
 * The CSV reader performs blocking file reads on [Dispatchers.IO]. Batched
 * graph writes stay in the caller coroutine context so backend implementations
 * keep control over their own dispatcher policy.
 *
 * ```kotlin
 * val importer = SuspendCsvGraphBulkImporter()
 * val source = CsvGraphImportSource(
 *     vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
 *     edges    = GraphImportSource.PathSource(Paths.get("edges.csv")),
 * )
 * val report = importer.importGraphSuspending(source, suspendOps, GraphImportOptions())
 * println("imported ${report.verticesCreated} vertices - ${report.status}")
 * ```
 */
class SuspendCsvGraphBulkImporter : GraphSuspendBulkImporter<CsvGraphImportSource> {

    override suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraphSuspending(source, operations, options, CsvGraphIoOptions())

    override suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport = importGraphSuspending(source, operations, options, CsvGraphIoOptions(), listener)

    suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(source.vertices),
                    GraphIoPaths.sizeOf(source.edges),
                )
            },
        )
        return reporter.runSuspending(
            block = { importGraphSuspending(source, operations, options, csvOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport {
        log.debug { "Starting CSV import (suspend): defaultVertexLabel=${options.defaultVertexLabel}, defaultEdgeLabel=${options.defaultEdgeLabel}" }
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
        val batchWriter = SuspendGraphIoBatchWriter(operations, options.writeBatchSize) { boundary, error ->
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
        try {
            parser.records(
                source = source.vertices,
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.VERTICES,
            ).collect { record ->
                if (status == GraphIoStatus.FAILED) return@collect
                verticesRead++
                if (checkpoint.shouldSkipVertex(verticesRead)) return@collect
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
        } catch (error: GraphIoReadException) {
            verticesCreated += batchWriter.flushVertices(idMap)
            checkpoint.verticesCommitted(verticesRead)
            failures += error.failure
            status = GraphIoStatus.FAILED
            checkpoint.failed("VERTICES", error.message)
        }

        if (status == GraphIoStatus.FAILED) {
            checkpoint.failed("VERTICES")
            log.warn { "CSV import (suspend) failed during vertex pass: vertices=$verticesCreated/$verticesRead, elapsed=${watch.elapsed()}" }
            return buildReport(
                watch, failures, GraphIoStatus.FAILED,
                verticesRead, verticesCreated, edgesRead, edgesCreated, skippedVertices, skippedEdges
            )
        }

        verticesCreated += batchWriter.flushVertices(idMap)
        checkpoint.verticesCommitted(verticesRead)

        // --- 엣지 패스 ---
        try {
            parser.records(
                source = source.edges,
                phase = GraphIoPhase.READ_EDGE,
                fileRole = GraphIoFileRole.EDGES,
            ).collect { record ->
                if (status == GraphIoStatus.FAILED) return@collect
                edgesRead++
                if (checkpoint.shouldSkipEdge(edgesRead)) return@collect
                val label = record.getString("label").orEmpty().ifBlank { options.defaultEdgeLabel }
                val from = record.getString("from").orEmpty()
                val to = record.getString("to").orEmpty()
                val fromId = idMap.resolve(from)
                val toId = idMap.resolve(to)
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
                            return@collect
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
                            return@collect
                        }
                    }
                }
                val rowMap: Map<String, String?> = record.toColumnMap()
                val props = buildMap<String, Any?> {
                    putAll(codec.extractProperties(rowMap))
                    val externalEdgeId = record.getString("id")?.takeIf { it.isNotBlank() }
                    externalEdgeId?.let { eid ->
                        options.preserveExternalIdProperty?.let { key -> put(key, eid) }
                    }
                }
                val created = batchWriter.addEdge(label, fromId, toId, props)
                edgesCreated += created
                if (created > 0) {
                    edgesCreated += batchWriter.flushEdges()
                    checkpoint.edgesCommitted(verticesRead, edgesRead)
                }
            }
        } catch (error: GraphIoReadException) {
            edgesCreated += batchWriter.flushEdges()
            checkpoint.edgesCommitted(verticesRead, edgesRead)
            failures += error.failure
            status = GraphIoStatus.FAILED
            checkpoint.failed("EDGES", error.message)
        }

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
            log.debug { "CSV import (suspend) completed: vertices=$verticesCreated/$verticesRead, edges=$edgesCreated/$edgesRead, skipped=$skippedVertices/$skippedEdges, status=$status, elapsed=${watch.elapsed()}" }
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

    companion object : KLoggingChannel()
}

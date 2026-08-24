package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.CsvRecordWriter
import io.bluetape4k.graph.io.contract.GraphSuspendBulkExporter
import io.bluetape4k.graph.io.csv.internal.CsvRecordCodec
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoRecordSpool
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.io.support.resolveLabels
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * Coroutine bulk exporter for CSV graph data.
 *
 * Graph reads stay in the caller coroutine context while blocking file writes
 * are isolated on [Dispatchers.IO].
 *
 * ```kotlin
 * val exporter = SuspendCsvGraphBulkExporter()
 * val sink = CsvGraphExportSink(
 *     vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
 *     edges    = GraphExportSink.PathSink(Paths.get("edges.csv")),
 * )
 * val report = coroutineScope {
 *     exporter.exportGraphSuspending(sink, suspendOps, GraphExportOptions(vertexLabels = setOf("Person")))
 * }
 * println("exported ${report.verticesWritten} vertices - ${report.status}")
 * ```
 */
class SuspendCsvGraphBulkExporter : GraphSuspendBulkExporter<CsvGraphExportSink> {

    override suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, CsvGraphIoOptions())

    override suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, CsvGraphIoOptions(), listener)

    suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
        listener: GraphIoProgressListener,
    ): GraphExportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.CSV,
            listener = listener,
            bytesProvider = {
                GraphIoPaths.sumSizes(
                    GraphIoPaths.sizeOf(sink.vertices),
                    GraphIoPaths.sizeOf(sink.edges),
                )
            },
        )
        return reporter.runSuspending(
            block = { exportGraphSuspending(sink, operations, options, csvOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphExportReport {
        log.debug { "Starting CSV export (suspend): vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L
        var eWritten = 0L
        val (vertexLabels, edgeLabels) = options.resolveLabels(operations)

        val spool = GraphIoRecordSpool()
        try {
            // 입력은 caller context에서 읽고, blocking spool 쓰기만 IO dispatcher에서 수행한다.
            for (label in vertexLabels) {
                operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize).collect { chunk ->
                    withContext(Dispatchers.IO) {
                        spool.appendVertices(chunk.map { v -> GraphIoVertexRecord(v.id.value, v.label, v.properties) })
                    }
                }
            }
            for (label in edgeLabels) {
                operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize).collect { chunk ->
                    withContext(Dispatchers.IO) {
                        spool.appendEdges(
                            chunk.map { e ->
                                GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
                            },
                        )
                    }
                }
            }
            withContext(Dispatchers.IO) { spool.finish() }

            withContext(Dispatchers.IO) {
                // --- 정점 익스포트 ---
                val vHeader = codec.unionVertexHeader(spool.vertexRecords().asIterable())
                val prefix = (csvOptions.propertyMode as? CsvPropertyMode.PrefixedColumns)?.prefix ?: ""
                GraphIoPaths.openWriter(sink.vertices).use { w ->
                    val csv = CsvRecordWriter(w)
                    csv.writeHeaders(vHeader)
                    for (v in spool.vertexRecords()) {
                        val row = buildList<Any?> {
                            add(v.externalId)
                            add(v.label)
                            vHeader.drop(2).forEach { col ->
                                val key = col.removePrefix(prefix)
                                add(v.properties[key]?.toString() ?: "")
                            }
                        }
                        csv.writeRow(row)
                        vWritten++
                    }
                }

                // --- 간선 익스포트 ---
                val eHeader = codec.unionEdgeHeader(spool.edgeRecords().asIterable())
                GraphIoPaths.openWriter(sink.edges).use { w ->
                    val csv = CsvRecordWriter(w)
                    csv.writeHeaders(eHeader)
                    for (ed in spool.edgeRecords()) {
                        val row = buildList<Any?> {
                            add(ed.externalId ?: "")
                            add(ed.label)
                            add(ed.fromExternalId)
                            add(ed.toExternalId)
                            eHeader.drop(4).forEach { col ->
                                val key = col.removePrefix(prefix)
                                add(ed.properties[key]?.toString() ?: "")
                            }
                        }
                        csv.writeRow(row)
                        eWritten++
                    }
                }
            }

            val status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL
            return GraphExportReport(
                status = status,
                format = GraphIoFormat.CSV,
                verticesWritten = vWritten,
                edgesWritten = eWritten,
                elapsed = watch.elapsed(),
                failures = failures,
            ).also {
                log.debug {
                    "CSV export (suspend) completed: verticesWritten=$vWritten, edgesWritten=$eWritten, " +
                        "status=$status, elapsed=${watch.elapsed()}"
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { spool.close() }
        }
    }

    companion object : KLoggingChannel()
}

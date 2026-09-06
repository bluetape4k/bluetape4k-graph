package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.CsvRecordWriter
import io.bluetape4k.graph.io.contract.GraphBulkExporter
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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * CSV 동기 벌크 익스포터.
 *
 * 정점 파일과 간선 파일을 별도로 작성한다.
 * 입력은 bounded chunk로 디스크 spool에 한 번만 기록하고 헤더와 본문에서 replay한다.
 *
 * ```kotlin
 * val exporter = CsvGraphBulkExporter()
 * val sink = CsvGraphExportSink(
 *     vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
 *     edges    = GraphExportSink.PathSink(Paths.get("edges.csv")),
 * )
 * val options = GraphExportOptions(
 *     vertexLabels = setOf("Person", "Company"),
 *     edgeLabels   = setOf("KNOWS", "WORKS_FOR"),
 * )
 * val report = exporter.exportGraph(sink, graphOps, options)
 * println("exported ${report.verticesWritten} vertices in ${report.elapsed.toMillis()}ms")
 * ```
 */
class CsvGraphBulkExporter : GraphBulkExporter<CsvGraphExportSink> {

    override fun exportGraph(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, operations, options, CsvGraphIoOptions())

    override fun exportGraph(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener,
    ): GraphExportReport = exportGraph(sink, operations, options, CsvGraphIoOptions(), listener)

    fun exportGraph(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
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
        return reporter.run(
            block = { exportGraph(sink, operations, options, csvOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "TooGenericExceptionCaught")
    fun exportGraph(
        sink: CsvGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphExportReport {
        log.debug { "Starting CSV export: vertexLabels=${options.vertexLabels}, edgeLabels=${options.edgeLabels}" }
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L
        var eWritten = 0L
        val (vertexLabels, edgeLabels) = options.resolveLabels(operations)

        val spool = GraphIoRecordSpool()
        var primaryFailure: Throwable? = null
        try {
            // 입력은 한 번만 읽어 immutable disk snapshot으로 고정한다.
            for (label in vertexLabels) {
                operations.findVerticesByLabelChunked(label, chunkSize = options.exportChunkSize).forEach { chunk ->
                    spool.appendVertices(
                        chunk.map { v ->
                            GraphIoVertexRecord(v.id.value, v.label, codec.prepareForSpool(v.properties))
                        },
                    )
                }
            }
            for (label in edgeLabels) {
                operations.findEdgesByLabelChunked(label, chunkSize = options.exportChunkSize).forEach { chunk ->
                    spool.appendEdges(
                        chunk.map { e ->
                            GraphIoEdgeRecord(
                                e.id.value,
                                e.label,
                                e.startId.value,
                                e.endId.value,
                                codec.prepareForSpool(e.properties),
                            )
                        },
                    )
                }
            }
            spool.finish()

            // --- 정점 익스포트 ---
            val vHeader = codec.unionVertexHeader(spool.vertexRecords().asIterable())
            GraphIoPaths.openWriter(sink.vertices).use { w ->
                val csv = CsvRecordWriter(w)
                csv.writeHeaders(vHeader)
                for (v in spool.vertexRecords()) {
                    val row = buildList<Any?> {
                        add(v.externalId)
                        add(v.label)
                        vHeader.drop(2).forEach { col ->
                            add(codec.encodeProperty(col, v.properties))
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
                            add(codec.encodeProperty(col, ed.properties))
                        }
                    }
                    csv.writeRow(row)
                    eWritten++
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
                    "CSV export completed: verticesWritten=$vWritten, edgesWritten=$eWritten, " +
                        "status=$status, elapsed=${watch.elapsed()}"
                }
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            spool.closeSuppressing(primaryFailure)
        }
    }

    companion object : KLogging()
}

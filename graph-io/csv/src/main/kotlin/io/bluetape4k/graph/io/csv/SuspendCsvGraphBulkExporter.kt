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
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/**
 * CSV 코루틴(suspend) 벌크 익스포터.
 * suspend 방식으로 정점과 간선을 Flow로 수집하여 CSV 파일로 저장한다.
 */
class SuspendCsvGraphBulkExporter : GraphSuspendBulkExporter<CsvGraphExportSink> {

    override suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraphSuspending(sink, operations, options, CsvGraphIoOptions())

    suspend fun exportGraphSuspending(
        sink: CsvGraphExportSink,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphExportReport = withContext(Dispatchers.IO) {
        val watch = GraphIoStopwatch()
        val codec = CsvRecordCodec(csvOptions.propertyMode)
        val failures = mutableListOf<GraphIoFailure>()
        var vWritten = 0L
        var eWritten = 0L

        // --- 정점 익스포트 ---
        val allVertices = options.vertexLabels.flatMap { label ->
            operations.findVerticesByLabel(label).toList().map { v ->
                GraphIoVertexRecord(v.id.value, v.label, v.properties)
            }
        }
        val vHeader = codec.unionVertexHeader(allVertices)
        val prefix = (csvOptions.propertyMode as? CsvPropertyMode.PrefixedColumns)?.prefix ?: ""
        GraphIoPaths.openWriter(sink.vertices).use { w ->
            val csv = CsvRecordWriter(w)
            csv.writeHeaders(vHeader)
            for (v in allVertices) {
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
            csv.close()
        }

        // --- 간선 익스포트 ---
        val allEdges = options.edgeLabels.flatMap { label ->
            operations.findEdgesByLabel(label).toList().map { e ->
                GraphIoEdgeRecord(e.id.value, e.label, e.startId.value, e.endId.value, e.properties)
            }
        }
        val eHeader = codec.unionEdgeHeader(allEdges)
        GraphIoPaths.openWriter(sink.edges).use { w ->
            val csv = CsvRecordWriter(w)
            csv.writeHeaders(eHeader)
            for (ed in allEdges) {
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
            csv.close()
        }

        GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.CSV,
            verticesWritten = vWritten,
            edgesWritten = eWritten,
            elapsed = watch.elapsed(),
            failures = failures,
        )
    }

    companion object : KLoggingChannel()
}

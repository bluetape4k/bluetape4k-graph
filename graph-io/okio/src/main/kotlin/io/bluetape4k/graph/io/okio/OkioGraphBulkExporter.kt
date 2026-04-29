package io.bluetape4k.graph.io.okio

import io.bluetape4k.graph.io.okio.bridge.asClosingOutputStream
import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import okio.FileSystem
import java.io.IOException

/**
 * OkIO 기반 동기 벌크 익스포터.
 *
 * 포맷은 호출자가 [GraphIoFormat]으로 명시적으로 지정한다. 확장자 기반 스니핑은 지원하지 않는다.
 *
 * ### CSV 제약
 * CSV 포맷은 정점/간선 파일을 분리해야 하므로, [OkioGraphExportSink.PathSink]만 지원한다.
 * 파일 경로 `{stem}` 기준으로 `{stem}_vertices.csv` 와 `{stem}_edges.csv` 에 쓴다.
 * SinkBased/OutputStreamBased + CSV 조합은 [UnsupportedOperationException]을 던진다.
 */
class OkioGraphBulkExporter(
    private val csvExporter: CsvGraphBulkExporter = CsvGraphBulkExporter(),
    private val jackson2Exporter: Jackson2NdJsonBulkExporter = Jackson2NdJsonBulkExporter(),
    private val jackson3Exporter: Jackson3NdJsonBulkExporter = Jackson3NdJsonBulkExporter(),
    private val graphmlExporter: GraphMlBulkExporter = GraphMlBulkExporter(),
) : GraphBulkExporter<OkioGraphExportSink> {

    companion object : KLogging()

    /**
     * OkIO 싱크로 그래프를 동기 익스포트한다. 포맷 기본값: NDJSON_JACKSON3.
     */
    override fun exportGraph(
        sink: OkioGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

    /**
     * 포맷을 명시적으로 지정하여 익스포트한다.
     *
     * @param format 익스포트 포맷. 확장자 기반 추론 없음 — 호출자가 반드시 지정.
     * @throws IOException I/O 오류 시
     * @throws UnsupportedOperationException CSV + 스트림 기반 싱크 조합 시
     */
    @Throws(IOException::class)
    fun exportGraph(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport {
        log.debug { "Starting OkIO export: format=$format, sink=${describeSink(sink)}" }
        return when (format) {
            GraphIoFormat.CSV -> exportCsv(sink, operations, options)
            GraphIoFormat.NDJSON_JACKSON2 -> exportSingleStream(sink) { os ->
                jackson2Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
            }
            GraphIoFormat.NDJSON_JACKSON3 -> exportSingleStream(sink) { os ->
                jackson3Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
            }
            GraphIoFormat.GRAPHML -> exportSingleStream(sink) { os ->
                graphmlExporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
            }
        }
    }

    // ─── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    /** 단일 스트림 익스포트 — OkIO sink를 OutputStream으로 변환하여 [block]에 전달한다. */
    private inline fun exportSingleStream(
        sink: OkioGraphExportSink,
        block: (java.io.OutputStream) -> GraphExportReport,
    ): GraphExportReport {
        return GraphIoOkioPaths.openSink(sink).use { bs ->
            bs.asClosingOutputStream().use { os -> block(os) }
        }
    }

    /**
     * CSV 익스포트: PathSink 기준으로 `{stem}_vertices.csv` + `{stem}_edges.csv` 파생.
     *
     * CSV 는 정점/간선 파일을 분리하는 포맷이므로 PathSink 만 지원한다.
     * SinkBased/OutputStreamBased 는 [UnsupportedOperationException] 을 던진다.
     */
    private fun exportCsv(
        sink: OkioGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        return when (sink) {
            is OkioGraphExportSink.PathSink -> {
                require(sink.fileSystem == FileSystem.SYSTEM) {
                    "CSV export은 시스템 파일시스템(FileSystem.SYSTEM)만 지원합니다. " +
                        "커스텀 FileSystem(FakeFileSystem 등)을 사용하려면 CsvGraphBulkExporter를 직접 사용하세요. " +
                        "제공된 FileSystem: ${sink.fileSystem}"
                }
                val stem = sink.path.toString().removeSuffix(".csv")
                val verticesSink = GraphExportSink.PathSink(java.nio.file.Paths.get("${stem}_vertices.csv"))
                val edgesSink = GraphExportSink.PathSink(java.nio.file.Paths.get("${stem}_edges.csv"))
                csvExporter.exportGraph(CsvGraphExportSink(verticesSink, edgesSink), operations, options)
            }
            else -> throw UnsupportedOperationException(
                "CSV 포맷은 두 파일(vertices/edges)이 필요하므로 PathSink 만 지원합니다. " +
                    "스트림 기반 싱크에서는 CsvGraphBulkExporter 를 직접 사용하세요."
            )
        }
    }

    private fun describeSink(sink: OkioGraphExportSink): String = when (sink) {
        is OkioGraphExportSink.PathSink -> sink.path.toString()
        is OkioGraphExportSink.SinkBased -> "<Sink>"
        is OkioGraphExportSink.OutputStreamBased -> "<OutputStream>"
    }
}

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
import io.bluetape4k.okio.tink.DEFAULT_DAEAD_CHUNK_SIZE
import io.bluetape4k.tink.daead.TinkDeterministicAead
import okio.BufferedSink
import okio.FileSystem
import java.io.IOException

/**
 * OkIO 소스와 싱크를 사용하는 동기 그래프 벌크 익스포터.
 *
 * 호출자는 [GraphIoFormat]을 명시적으로 전달해야 한다. 파일 확장자 기반 포맷 추론은 의도적으로 지원하지 않는다.
 *
 * ### CSV 제약
 * CSV는 정점 파일과 간선 파일을 분리해야 하므로, 이 facade는 CSV에 한해 [OkioGraphExportSink.PathSink]만
 * 지원한다. 대상 stem에서 `{stem}_vertices.csv`와 `{stem}_edges.csv`를 생성한다.
 * 스트림 기반 싱크는 [UnsupportedOperationException]을 던진다.
 */
class OkioGraphBulkExporter(
    private val csvExporter: CsvGraphBulkExporter = CsvGraphBulkExporter(),
    private val jackson2Exporter: Jackson2NdJsonBulkExporter = Jackson2NdJsonBulkExporter(),
    private val jackson3Exporter: Jackson3NdJsonBulkExporter = Jackson3NdJsonBulkExporter(),
    private val graphmlExporter: GraphMlBulkExporter = GraphMlBulkExporter(),
) : GraphBulkExporter<OkioGraphExportSink> {

    companion object : KLogging()

    /**
     * 그래프를 OkIO 싱크에 [GraphIoFormat.NDJSON_JACKSON3] 포맷으로 익스포트한다.
     */
    override fun exportGraph(
        sink: OkioGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

    /**
     * 그래프를 OkIO 싱크에 명시된 [format]으로 익스포트한다.
     *
     * @param format 사용할 익스포트 포맷. 확장자 기반 추론은 수행하지 않는다.
     * @throws IOException I/O 오류가 발생한 경우.
     * @throws UnsupportedOperationException CSV를 스트림 기반 싱크와 함께 사용한 경우.
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

    /**
     * DAEAD chunk 암호화를 통해 단일 스트림 그래프 포맷을 익스포트한다.
     *
     * CSV는 pair 파일 포맷이므로 의도적으로 지원하지 않는다. 사용자 정의 CSV 파일 쌍에는 저수준
     * [GraphIoOkioPaths.openDaeadEncryptedSink] helper를 직접 사용한다.
     *
     * @throws IOException I/O 또는 암호화 실패가 발생한 경우.
     * @throws UnsupportedOperationException [format]이 [GraphIoFormat.CSV]인 경우.
     */
    @Throws(IOException::class)
    fun exportGraphDaead(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): GraphExportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO DAEAD export: format=$format, sink=${describeSink(sink)}" }
        return GraphIoOkioPaths.openDaeadEncryptedSink(sink, daead, chunkSize, associatedData).use { bs ->
            exportSingleStream(bs, format, operations, options)
        }
    }

    /**
     * GZip 압축 후 DAEAD chunk 암호화(compress-then-encrypt)로 단일 스트림 그래프 포맷을 익스포트한다.
     *
     * 대응되는 역방향 임포트 경로는 [OkioGraphBulkImporter.importGraphDaeadGzip]이다.
     */
    @Throws(IOException::class)
    fun exportGraphGzipDaead(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): GraphExportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO gzip+DAEAD export: format=$format, sink=${describeSink(sink)}" }
        return GraphIoOkioPaths.openGzipDaeadEncryptedSink(sink, daead, chunkSize, associatedData).use { bs ->
            exportSingleStream(bs, format, operations, options)
        }
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    /** OkIO 싱크를 output stream으로 변환한 뒤 단일 스트림 포맷을 익스포트한다. */
    private inline fun exportSingleStream(
        sink: OkioGraphExportSink,
        block: (java.io.OutputStream) -> GraphExportReport,
    ): GraphExportReport {
        return GraphIoOkioPaths.openSink(sink).use { bs ->
            bs.asClosingOutputStream().use { os -> block(os) }
        }
    }

    private fun exportSingleStream(
        sink: BufferedSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport =
        sink.asClosingOutputStream().use { os ->
            when (format) {
                GraphIoFormat.NDJSON_JACKSON2 ->
                    jackson2Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
                GraphIoFormat.NDJSON_JACKSON3 ->
                    jackson3Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
                GraphIoFormat.GRAPHML ->
                    graphmlExporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
                GraphIoFormat.CSV -> unsupportedCsvEncrypted()
            }
        }

    /**
     * [OkioGraphExportSink.PathSink]에서 `{stem}_vertices.csv`와 `{stem}_edges.csv`를 파생해 CSV를 익스포트한다.
     *
     * CSV는 pair 파일 포맷이므로 스트림 기반 싱크는 [UnsupportedOperationException]을 던진다.
     */
    private fun exportCsv(
        sink: OkioGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        return when (sink) {
            is OkioGraphExportSink.PathSink -> {
                require(sink.fileSystem == FileSystem.SYSTEM) {
                    "CSV export supports only FileSystem.SYSTEM. " +
                        "Use CsvGraphBulkExporter directly for custom FileSystem instances. " +
                        "Provided FileSystem: ${sink.fileSystem}"
                }
                val stem = sink.path.toString().removeSuffix(".csv")
                val verticesSink = GraphExportSink.PathSink(java.nio.file.Paths.get("${stem}_vertices.csv"))
                val edgesSink = GraphExportSink.PathSink(java.nio.file.Paths.get("${stem}_edges.csv"))
                csvExporter.exportGraph(CsvGraphExportSink(verticesSink, edgesSink), operations, options)
            }
            else -> throw UnsupportedOperationException(
                "CSV requires two files for vertices and edges, so OkioGraphBulkExporter supports only PathSink. " +
                    "Use CsvGraphBulkExporter directly for stream-backed sinks."
            )
        }
    }

    private fun describeSink(sink: OkioGraphExportSink): String = when (sink) {
        is OkioGraphExportSink.PathSink -> sink.path.toString()
        is OkioGraphExportSink.SinkBased -> "<Sink>"
        is OkioGraphExportSink.OutputStreamBased -> "<OutputStream>"
    }

    private fun requireSingleStreamFormat(format: GraphIoFormat) {
        if (format == GraphIoFormat.CSV) {
            unsupportedCsvEncrypted()
        }
    }

    private fun unsupportedCsvEncrypted(): Nothing =
        throw UnsupportedOperationException(
            "CSV is a paired-file format. Use low-level DAEAD helpers directly for custom CSV file pairs."
        )
}

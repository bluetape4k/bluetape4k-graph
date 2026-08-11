@file:Suppress("TooManyFunctions")

package io.bluetape4k.graph.io.okio

import io.bluetape4k.graph.io.okio.bridge.toInputStream
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.okio.tink.DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH
import io.bluetape4k.tink.daead.TinkDeterministicAead
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.IOException

/**
 * OkIO 소스를 사용하는 동기 그래프 벌크 임포터.
 *
 * 호출자는 [GraphIoFormat]을 명시적으로 전달해야 한다. 파일 확장자 기반 포맷 추론은 의도적으로 지원하지 않는다.
 *
 * ### CSV 제약
 * CSV는 정점 파일과 간선 파일을 분리해야 하므로, 이 facade는 CSV에 한해 [OkioGraphImportSource.PathSource]만
 * 지원한다. 소스 stem에서 `{stem}_vertices.csv`와 `{stem}_edges.csv`를 파생한다.
 * 스트림 기반 소스는 [UnsupportedOperationException]을 던진다.
 *
 * ### 위임
 * CSV, Jackson 2, Jackson 3, GraphML 전용 임포터에는 변환된 input stream을 전달한다.
 * 압축 체이닝과 [okio.FileSystem] 지원 같은 OkIO 전용 동작은 [GraphIoOkioPaths]가 담당한다.
 */
class OkioGraphBulkImporter(
    private val csvImporter: CsvGraphBulkImporter = CsvGraphBulkImporter(),
    private val jackson2Importer: Jackson2NdJsonBulkImporter = Jackson2NdJsonBulkImporter(),
    private val jackson3Importer: Jackson3NdJsonBulkImporter = Jackson3NdJsonBulkImporter(),
    private val graphmlImporter: GraphMlBulkImporter = GraphMlBulkImporter(),
) : GraphBulkImporter<OkioGraphImportSource> {

    companion object : KLogging()

    /**
     * OkIO 소스에서 [GraphIoFormat.NDJSON_JACKSON3] 포맷으로 그래프를 임포트한다.
     *
     * @param source 데이터를 읽을 OkIO 임포트 소스.
     * @param operations 정점과 간선을 생성할 대상 그래프 작업 API.
     * @param options 중복 처리, 누락 endpoint 처리, 기본 label, 배치 크기를 제어하는 임포트 옵션.
     * @throws IOException I/O 오류가 발생한 경우.
     * @throws UnsupportedOperationException CSV를 스트림 기반 소스와 함께 사용한 경우.
     */
    override fun importGraph(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

    override fun importGraph(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport = importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

    /**
     * OkIO 소스에서 명시된 [format]으로 그래프를 임포트한다.
     *
     * @param format 사용할 임포트 포맷. 확장자 기반 추론은 수행하지 않는다.
     */
    @Throws(IOException::class)
    fun importGraph(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport {
        log.debug { "Starting OkIO import: format=$format, source=${describeSource(source)}" }
        return when (format) {
            GraphIoFormat.CSV -> importCsv(source, operations, options)
            GraphIoFormat.NDJSON_JACKSON2 -> importSingleStream(source) { is_ ->
                jackson2Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
            }
            GraphIoFormat.NDJSON_JACKSON3 -> importSingleStream(source) { is_ ->
                jackson3Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
            }
            GraphIoFormat.GRAPHML -> importSingleStream(source) { is_ ->
                graphmlImporter.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
            }
        }
    }

    /** 명시한 포맷과 진행 listener를 함께 사용하는 OkIO 임포트 오버로드. */
    fun importGraph(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = format,
            listener = listener,
            bytesProvider = {
                if (format == GraphIoFormat.CSV) GraphIoOkioPaths.sizeOfCsv(source)
                else GraphIoOkioPaths.sizeOf(source)
            },
        )
        return reporter.run(
            block = { importGraph(source, format, operations, options) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    /**
     * DAEAD chunk 복호화를 통해 단일 스트림 그래프 포맷을 임포트한다.
     *
     * CSV는 pair 파일 포맷이므로 의도적으로 지원하지 않는다. 사용자 정의 CSV 파일 쌍에는 저수준 DAEAD helper를
     * 직접 사용한다.
     */
    @Throws(IOException::class)
    fun importGraphDaead(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
    ): GraphImportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO DAEAD import: format=$format, source=${describeSource(source)}" }
        return GraphIoOkioPaths.openDaeadDecryptedSource(source, daead, associatedData, maxCiphertextLength).use { bs ->
            importSingleStream(bs, format, operations, options)
        }
    }

    fun importGraphDaead(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = format,
            listener = listener,
            bytesProvider = { GraphIoOkioPaths.sizeOf(source) },
        )
        return reporter.run(
            block = {
                importGraphDaead(
                    source,
                    format,
                    daead,
                    operations,
                    options,
                    associatedData,
                    maxCiphertextLength,
                )
            },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    /**
     * DAEAD chunk 복호화 후 GZip 압축 해제(decrypt-then-inflate) 소스에서 단일 스트림 그래프 포맷을 임포트한다.
     *
     * 입력은 [OkioGraphBulkExporter.exportGraphGzipDaead]로 기록된 데이터여야 한다.
     */
    @Throws(IOException::class)
    fun importGraphDaeadGzip(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
        maxDecompressedBytes: Long = GraphIoOkioPaths.DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): GraphImportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO DAEAD+gzip import: format=$format, source=${describeSource(source)}" }
        return GraphIoOkioPaths.openDaeadDecryptedGzipSource(
            source = source,
            daead = daead,
            associatedData = associatedData,
            maxCiphertextLength = maxCiphertextLength,
            maxDecompressedBytes = maxDecompressedBytes,
        ).use { bs ->
            importSingleStream(bs, format, operations, options)
        }
    }

    fun importGraphDaeadGzip(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
        maxDecompressedBytes: Long = GraphIoOkioPaths.DEFAULT_MAX_DECOMPRESSED_BYTES,
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = format,
            listener = listener,
            bytesProvider = { GraphIoOkioPaths.sizeOf(source) },
        )
        return reporter.run(
            block = {
                importGraphDaeadGzip(
                    source,
                    format,
                    daead,
                    operations,
                    options,
                    associatedData,
                    maxCiphertextLength,
                    maxDecompressedBytes,
                )
            },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    /** OkIO 소스를 input stream으로 변환한 뒤 단일 스트림 포맷을 임포트한다. */
    private inline fun importSingleStream(
        source: OkioGraphImportSource,
        block: (java.io.InputStream) -> GraphImportReport,
    ): GraphImportReport {
        return GraphIoOkioPaths.openSource(source).use { bs ->
            bs.toInputStream().use { is_ -> block(is_) }
        }
    }

    private fun importSingleStream(
        source: BufferedSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport =
        source.toInputStream().use { is_ ->
            when (format) {
                GraphIoFormat.NDJSON_JACKSON2 ->
                    jackson2Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
                GraphIoFormat.NDJSON_JACKSON3 ->
                    jackson3Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
                GraphIoFormat.GRAPHML ->
                    graphmlImporter.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
                GraphIoFormat.CSV -> unsupportedCsvEncrypted()
            }
        }

    /**
     * [OkioGraphImportSource.PathSource]에서 `{stem}_vertices.csv`와 `{stem}_edges.csv`를 파생해 CSV를 임포트한다.
     *
     * CSV는 pair 파일 포맷이므로 스트림 기반 소스는 [UnsupportedOperationException]을 던진다.
     */
    private fun importCsv(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        return when (source) {
            is OkioGraphImportSource.PathSource -> {
                require(source.fileSystem == FileSystem.SYSTEM) {
                    "CSV import supports only FileSystem.SYSTEM. " +
                        "Use CsvGraphBulkImporter directly for custom FileSystem instances. " +
                        "Provided FileSystem: ${source.fileSystem}"
                }
                val stem = source.path.toString().removeSuffix(".csv")
                val verticesPath = "${stem}_vertices.csv".toPath()
                val edgesPath = "${stem}_edges.csv".toPath()
                val csvSource = CsvGraphImportSource(
                    vertices = GraphImportSource.PathSource(java.nio.file.Paths.get(verticesPath.toString())),
                    edges = GraphImportSource.PathSource(java.nio.file.Paths.get(edgesPath.toString())),
                )
                csvImporter.importGraph(csvSource, operations, options)
            }
            else -> throw UnsupportedOperationException(
                "CSV requires two files for vertices and edges, so OkioGraphBulkImporter supports only PathSource. " +
                    "Use CsvGraphBulkImporter directly for stream-backed sources."
            )
        }
    }

    private fun describeSource(source: OkioGraphImportSource): String = when (source) {
        is OkioGraphImportSource.PathSource -> source.path.toString()
        is OkioGraphImportSource.SourceBased -> "<Source>"
        is OkioGraphImportSource.InputStreamBased -> "<InputStream>"
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

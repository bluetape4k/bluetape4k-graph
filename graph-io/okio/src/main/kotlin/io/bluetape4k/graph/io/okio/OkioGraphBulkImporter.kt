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
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.IOException

/**
 * OkIO 기반 동기 벌크 임포터.
 *
 * 포맷은 호출자가 [GraphIoFormat]으로 명시적으로 지정한다. 확장자 기반 스니핑은 지원하지 않는다.
 *
 * ### CSV 제약
 * CSV 포맷은 정점/간선 파일을 분리해야 하므로, [OkioGraphImportSource.PathSource]만 지원한다.
 * 파일 경로 `{stem}` 기준으로 `{stem}_vertices.csv` 와 `{stem}_edges.csv` 를 자동으로 파생한다.
 * SourceBased/InputStreamBased + CSV 조합은 [UnsupportedOperationException]을 던진다.
 *
 * ### 위임 구조
 * 각 포맷별 기존 BulkImporter (CSV/Jackson2/Jackson3/GraphML) 에게 InputStream 으로 위임한다.
 * OkIO 의 이점(압축 체이닝, FileSystem 추상화)은 [GraphIoOkioPaths]에서 적용된다.
 */
class OkioGraphBulkImporter(
    private val csvImporter: CsvGraphBulkImporter = CsvGraphBulkImporter(),
    private val jackson2Importer: Jackson2NdJsonBulkImporter = Jackson2NdJsonBulkImporter(),
    private val jackson3Importer: Jackson3NdJsonBulkImporter = Jackson3NdJsonBulkImporter(),
    private val graphmlImporter: GraphMlBulkImporter = GraphMlBulkImporter(),
) : GraphBulkImporter<OkioGraphImportSource> {

    companion object : KLogging()

    /**
     * OkIO 소스로부터 [format] 포맷으로 그래프를 동기 임포트한다.
     *
     * @param source OkIO 기반 임포트 소스
     * @param operations 임포트 대상 그래프 API
     * @param options 중복/미존재 엔드포인트 정책, 기본 레이블, 배치 크기 등
     * @throws IOException I/O 오류 시
     * @throws UnsupportedOperationException CSV + 스트림 기반 소스 조합 시
     */
    override fun importGraph(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

    /**
     * 포맷을 명시적으로 지정하여 임포트한다.
     *
     * @param format 임포트 포맷. 확장자 기반 추론 없음 — 호출자가 반드시 지정.
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

    // ─── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    /** 단일 스트림 임포트 — OkIO source를 InputStream으로 변환하여 [block]에 전달한다. */
    private inline fun importSingleStream(
        source: OkioGraphImportSource,
        block: (java.io.InputStream) -> GraphImportReport,
    ): GraphImportReport {
        return GraphIoOkioPaths.openSource(source).use { bs ->
            bs.toInputStream().use { is_ -> block(is_) }
        }
    }

    /**
     * CSV 임포트: PathSource 기준으로 `{stem}_vertices.csv` + `{stem}_edges.csv` 파생.
     *
     * CSV 는 정점/간선 파일을 분리하는 포맷이므로 PathSource 만 지원한다.
     * SourceBased/InputStreamBased 는 [UnsupportedOperationException] 을 던진다.
     */
    private fun importCsv(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        return when (source) {
            is OkioGraphImportSource.PathSource -> {
                require(source.fileSystem == FileSystem.SYSTEM) {
                    "CSV import은 시스템 파일시스템(FileSystem.SYSTEM)만 지원합니다. " +
                        "커스텀 FileSystem(FakeFileSystem 등)을 사용하려면 CsvGraphBulkImporter를 직접 사용하세요. " +
                        "제공된 FileSystem: ${source.fileSystem}"
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
                "CSV 포맷은 두 파일(vertices/edges)이 필요하므로 PathSource 만 지원합니다. " +
                    "스트림 기반 소스에서는 CsvGraphBulkImporter 를 직접 사용하세요."
            )
        }
    }

    private fun describeSource(source: OkioGraphImportSource): String = when (source) {
        is OkioGraphImportSource.PathSource -> source.path.toString()
        is OkioGraphImportSource.SourceBased -> "<Source>"
        is OkioGraphImportSource.InputStreamBased -> "<InputStream>"
    }
}

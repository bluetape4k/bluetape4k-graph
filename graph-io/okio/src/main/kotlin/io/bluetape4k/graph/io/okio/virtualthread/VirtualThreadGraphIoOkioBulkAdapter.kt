package io.bluetape4k.graph.io.okio.virtualthread

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * OkIO 기반 Virtual Thread 비동기 벌크 임포터/익스포터.
 *
 * [OkioGraphBulkImporter] / [OkioGraphBulkExporter] 의 동기 API를 Java Virtual Thread 위에서 실행한다.
 * 반환된 [CompletableFuture]로 논블로킹 방식으로 결과를 받는다.
 *
 * close 책임: 이 어댑터는 내부적으로 Virtual Thread 풀을 관리하지 않는다.
 * 각 importGraphAsync / exportGraphAsync 호출은 독립적인 Virtual Thread 위에서 실행된다.
 */
class VirtualThreadGraphIoOkioBulkAdapter(
    private val importer: OkioGraphBulkImporter = OkioGraphBulkImporter(),
    private val exporter: OkioGraphBulkExporter = OkioGraphBulkExporter(),
) {

    companion object : KLogging()

    /**
     * OkIO 소스로부터 [format] 포맷으로 그래프를 Virtual Thread 비동기 임포트한다.
     *
     * @param source OkIO 기반 임포트 소스
     * @param format 임포트 포맷 — 호출자가 명시적으로 지정
     * @param operations 임포트 대상 그래프 API
     * @param options 임포트 옵션
     * @return 임포트 결과 보고서를 담은 [CompletableFuture]
     */
    fun importGraphAsync(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): CompletableFuture<GraphImportReport> =
        virtualFutureOf { importer.importGraph(source, format, operations, options) }

    /**
     * OkIO 싱크에 [format] 포맷으로 그래프를 Virtual Thread 비동기 익스포트한다.
     *
     * @param sink OkIO 기반 익스포트 싱크
     * @param format 익스포트 포맷 — 호출자가 명시적으로 지정
     * @param operations 익스포트 대상 그래프 API
     * @param options 익스포트 옵션
     * @return 익스포트 결과 보고서를 담은 [CompletableFuture]
     */
    fun exportGraphAsync(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): CompletableFuture<GraphExportReport> =
        virtualFutureOf { exporter.exportGraph(sink, format, operations, options) }
}

package io.bluetape4k.graph.io.okio.coroutines

import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportProgress
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportProgress
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import java.io.IOException

/**
 * OkIO 기반 코루틴(suspend) 벌크 임포터/익스포터.
 *
 * ### Flow 변형
 * [importGraph] / [exportGraph]: 진행 상태([GraphImportProgress] / [GraphExportProgress]) 스트림을 반환한다.
 * 현재 구현에서는 시작 및 완료 두 번 emit 한다. 세밀한 진행률 추적은 v2에서 추가 예정.
 *
 * ### Await 변형
 * [importGraphAwait] / [exportGraphAwait]: 완료 보고서를 직접 반환한다.
 *
 * ### 취소 안전성
 * 모든 blocking I/O는 [runInterruptible] 로 래핑하여 코루틴 취소 시 `Thread.interrupt()` 가 전달된다.
 * 리소스 정리(close)는 [OkioGraphBulkImporter] / [OkioGraphBulkExporter] 내부에서 처리하며,
 * 각 호출이 독립적으로 소스/싱크를 열고 닫으므로 어댑터 레벨 cleanup 은 불필요하다.
 *
 * ### close 책임
 * 이 어댑터 자체는 [AutoCloseable]이 아니다. 내부 [OkioGraphBulkImporter] / [OkioGraphBulkExporter]가
 * 관리하는 리소스를 직접 참조하지 않는다. 각 import/export 호출이 자체 리소스를 열고 닫는다.
 */
class SuspendGraphIoOkioBulkAdapter(
    private val importer: OkioGraphBulkImporter = OkioGraphBulkImporter(),
    private val exporter: OkioGraphBulkExporter = OkioGraphBulkExporter(),
) {

    companion object : KLogging()

    // ─── Flow (진행 상태 스트림) ────────────────────────────────────────────────

    /**
     * OkIO 소스로부터 [format] 포맷으로 그래프를 임포트하고 진행 상태 Flow를 반환한다.
     *
     * 현재 구현은 시작([GraphImportProgress.processed]=0)과 완료(실제 처리 수)를 emit한다.
     * Flow collect 완료 후 실제 결과를 얻으려면 [importGraphAwait]를 사용한다.
     *
     * @param source OkIO 기반 임포트 소스
     * @param format 임포트 포맷 — 호출자가 명시적으로 지정
     * @param operations 임포트 대상 그래프 API
     * @param options 임포트 옵션
     */
    fun importGraph(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): Flow<GraphImportProgress> = flow {
        emit(GraphImportProgress(processed = 0))
        val report = importGraphInternal(source, format, operations, options)
        emit(
            GraphImportProgress(
                processed = report.verticesRead + report.edgesRead,
                total = report.verticesRead + report.edgesRead,
            )
        )
    }

    fun importGraph(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        listener: GraphIoProgressListener,
    ): Flow<GraphImportProgress> = flow {
        emit(GraphImportProgress(processed = 0))
        val report = importGraphInternal(source, format, operations, options, listener)
        emit(
            GraphImportProgress(
                processed = report.verticesRead + report.edgesRead,
                total = report.verticesRead + report.edgesRead,
            )
        )
    }

    /**
     * OkIO 싱크에 [format] 포맷으로 그래프를 익스포트하고 진행 상태 Flow를 반환한다.
     *
     * 현재 구현은 시작([GraphExportProgress.exported]=0)과 완료(실제 익스포트 수)를 emit한다.
     *
     * @param sink OkIO 기반 익스포트 싱크
     * @param format 익스포트 포맷 — 호출자가 명시적으로 지정
     * @param operations 익스포트 대상 그래프 API
     * @param options 익스포트 옵션
     */
    fun exportGraph(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): Flow<GraphExportProgress> = flow {
        emit(GraphExportProgress(exported = 0))
        val report = exportGraphInternal(sink, format, operations, options)
        emit(
            GraphExportProgress(
                exported = report.verticesWritten + report.edgesWritten,
                total = report.verticesWritten + report.edgesWritten,
            )
        )
    }

    fun exportGraph(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        listener: GraphIoProgressListener,
    ): Flow<GraphExportProgress> = flow {
        emit(GraphExportProgress(exported = 0))
        val report = exportGraphInternal(sink, format, operations, options, listener)
        emit(
            GraphExportProgress(
                exported = report.verticesWritten + report.edgesWritten,
                total = report.verticesWritten + report.edgesWritten,
            )
        )
    }

    // ─── Await (완료 보고서) ────────────────────────────────────────────────────

    /**
     * OkIO 소스로부터 [format] 포맷으로 그래프를 임포트하고 완료 보고서를 반환한다.
     *
     * blocking I/O를 [runInterruptible] 로 래핑하여 코루틴 취소를 지원한다.
     * finally 에서 리소스가 반드시 닫히도록 `NonCancellable` 로 보호한다.
     *
     * @throws IOException I/O 오류 시
     */
    suspend fun importGraphAwait(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport = importGraphInternal(source, format, operations, options)

    suspend fun importGraphAwait(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        listener: GraphIoProgressListener,
    ): GraphImportReport = importGraphInternal(source, format, operations, options, listener)

    /**
     * OkIO 싱크에 [format] 포맷으로 그래프를 익스포트하고 완료 보고서를 반환한다.
     *
     * @throws IOException I/O 오류 시
     */
    suspend fun exportGraphAwait(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport = exportGraphInternal(sink, format, operations, options)

    suspend fun exportGraphAwait(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        listener: GraphIoProgressListener,
    ): GraphExportReport = exportGraphInternal(sink, format, operations, options, listener)

    // ─── 내부 구현 ─────────────────────────────────────────────────────────────

    @Throws(IOException::class)
    private suspend fun importGraphInternal(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener? = null,
    ): GraphImportReport {
        log.debug { "Starting OkIO import (suspend): format=$format" }
        return runInterruptible(Dispatchers.IO) {
            listener?.let { importer.importGraph(source, format, operations, options, it) }
                ?: importer.importGraph(source, format, operations, options)
        }
    }

    @Throws(IOException::class)
    private suspend fun exportGraphInternal(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions,
        listener: GraphIoProgressListener? = null,
    ): GraphExportReport {
        log.debug { "Starting OkIO export (suspend): format=$format" }
        return runInterruptible(Dispatchers.IO) {
            listener?.let { exporter.exportGraph(sink, format, operations, options, it) }
                ?: exporter.exportGraph(sink, format, operations, options)
        }
    }
}

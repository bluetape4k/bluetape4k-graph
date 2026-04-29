package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.okio.GraphIoOkioPaths
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.okio.bridge.asClosingOutputStream
import io.bluetape4k.graph.io.okio.bridge.toInputStream
import io.bluetape4k.graph.io.okio.coroutines.SuspendGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.okio.virtualthread.VirtualThreadGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportProgress
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportProgress
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.CompletableFuture

private val graphmlVtAdapter = VirtualThreadGraphIoOkioBulkAdapter()
private val graphmlSuspendAdapter = SuspendGraphIoOkioBulkAdapter()

// ─── Sync ─────────────────────────────────────────────────────────────────────

/**
 * GraphML 포맷으로 그래프를 OkIO 소스에서 임포트한다.
 *
 * StAX 파서는 [InputStream] 기반이므로 OkIO [okio.BufferedSource]를 InputStream 으로 변환하여 전달한다.
 * XXE 방지 하드닝: StAX 팩토리에 `IS_SUPPORTING_EXTERNAL_ENTITIES=false`, `SUPPORT_DTD=false` 적용 (기존 구현 위임).
 *
 * @param source OkIO 기반 임포트 소스
 * @param operations 임포트 대상 그래프 API
 * @param options 임포트 옵션
 */
fun GraphMlBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    return GraphIoOkioPaths.openSource(source).use { bs ->
        bs.toInputStream().use { is_ ->
            this.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
        }
    }
}

/**
 * GraphML 포맷(Gzip 압축)으로 그래프를 OkIO 소스에서 임포트한다.
 */
fun GraphMlBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    return GraphIoOkioPaths.openGzipSource(source).use { bs ->
        bs.toInputStream().use { is_ ->
            this.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
        }
    }
}

/**
 * 그래프를 GraphML 포맷으로 OkIO 싱크에 익스포트한다.
 *
 * StAX 라이터는 [java.io.OutputStream] 기반이므로 [asClosingOutputStream] 을 통해 close 체인을 보장한다.
 * `XMLStreamWriter.close()` 호출 후 OkIO [okio.BufferedSink]까지 닫힌다.
 */
fun GraphMlBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    return GraphIoOkioPaths.openSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
        }
    }
}

/**
 * 그래프를 GraphML 포맷(Gzip 압축)으로 OkIO 싱크에 익스포트한다.
 */
fun GraphMlBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    return GraphIoOkioPaths.openGzipSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
        }
    }
}

// ─── VirtualThread ────────────────────────────────────────────────────────────

/** GraphML OkIO Virtual Thread 비동기 임포트 */
fun GraphMlBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): CompletableFuture<GraphImportReport> =
    graphmlVtAdapter.importGraphAsync(source, GraphIoFormat.GRAPHML, operations, options)

/** GraphML OkIO Virtual Thread 비동기 익스포트 */
fun GraphMlBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): CompletableFuture<GraphExportReport> =
    graphmlVtAdapter.exportGraphAsync(sink, GraphIoFormat.GRAPHML, operations, options)

// ─── Suspend ─────────────────────────────────────────────────────────────────

/** GraphML OkIO 코루틴 진행 상태 Flow 임포트 */
fun GraphMlBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): Flow<GraphImportProgress> =
    graphmlSuspendAdapter.importGraph(source, GraphIoFormat.GRAPHML, operations, options)

/** GraphML OkIO 코루틴 await 임포트 (완료 보고서) */
suspend fun GraphMlBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport =
    graphmlSuspendAdapter.importGraphAwait(source, GraphIoFormat.GRAPHML, operations, options)

/** GraphML OkIO 코루틴 진행 상태 Flow 익스포트 */
fun GraphMlBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): Flow<GraphExportProgress> =
    graphmlSuspendAdapter.exportGraph(sink, GraphIoFormat.GRAPHML, operations, options)

/** GraphML OkIO 코루틴 await 익스포트 (완료 보고서) */
suspend fun GraphMlBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport =
    graphmlSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.GRAPHML, operations, options)

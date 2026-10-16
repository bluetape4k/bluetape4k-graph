package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.okio.asSource
import io.bluetape4k.graph.io.okio.bridge.asClosingOutputStream
import io.bluetape4k.graph.io.okio.coroutines.SuspendGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.okio.openGzipSink
import io.bluetape4k.graph.io.okio.openGzipSource
import io.bluetape4k.graph.io.okio.openSink
import io.bluetape4k.graph.io.okio.openSource
import io.bluetape4k.graph.io.okio.virtualthread.VirtualThreadGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphExportProgress
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportProgress
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.repository.GraphOperations
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.CompletableFuture

// 두 어댑터 모두 상태 없는 싱글턴 — Jackson2/3가 공유해도 무관
private val jacksonVtAdapter = VirtualThreadGraphIoOkioBulkAdapter.Default
private val jacksonSuspendAdapter = SuspendGraphIoOkioBulkAdapter.Default

// ─── Jackson 2 — Sync ─────────────────────────────────────────────────────────

/**
 * Jackson 2 NDJSON 포맷으로 그래프를 OkIO 소스에서 임포트한다.
 *
 * @param source OkIO 기반 임포트 소스
 * @param operations 임포트 대상 그래프 API
 * @param options 임포트 옵션
 */
fun Jackson2NdJsonBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    return source.openSource().use { bs ->
        this.importGraph(bs.asSource(true), operations, options)
    }
}

fun Jackson2NdJsonBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport {
    return source.openSource().use { bs ->
        this.importGraph(bs.asSource(true), operations, options, listener)
    }
}

/**
 * Jackson 2 NDJSON 포맷(Gzip 압축)으로 그래프를 OkIO 소스에서 임포트한다.
 */
fun Jackson2NdJsonBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    return source.openGzipSource().use { bs ->
        this.importGraph(bs.asSource(true), operations, options)
    }
}

fun Jackson2NdJsonBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport {
    return source.openGzipSource().use { bs ->
        this.importGraph(bs.asSource(true), operations, options, listener)
    }
}

/**
 * 그래프를 Jackson 2 NDJSON 포맷으로 OkIO 싱크에 익스포트한다.
 */
fun Jackson2NdJsonBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    return sink.openSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(os.asSink(false), operations, options)
        }
    }
}

fun Jackson2NdJsonBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport {
    return sink.openSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(
                os.asSink(false),
                operations,
                options,
                listener,
            )
        }
    }
}

/**
 * 그래프를 Jackson 2 NDJSON 포맷(Gzip 압축)으로 OkIO 싱크에 익스포트한다.
 */
fun Jackson2NdJsonBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    return sink.openGzipSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(os.asSink(false), operations, options)
        }
    }
}

fun Jackson2NdJsonBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport {
    return sink.openGzipSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(
                os.asSink(false),
                operations,
                options,
                listener,
            )
        }
    }
}

// ─── Jackson 2 — VirtualThread ────────────────────────────────────────────────

/** Jackson 2 NDJSON OkIO Virtual Thread 비동기 임포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): CompletableFuture<GraphImportReport> =
    jacksonVtAdapter.importGraphAsync(source, GraphIoFormat.NDJSON_JACKSON2, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): CompletableFuture<GraphImportReport> =
    jacksonVtAdapter.importGraphAsync(source, GraphIoFormat.NDJSON_JACKSON2, operations, options, listener)

/** Jackson 2 NDJSON OkIO Virtual Thread 비동기 익스포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): CompletableFuture<GraphExportReport> =
    jacksonVtAdapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): CompletableFuture<GraphExportReport> =
    jacksonVtAdapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options, listener)

// ─── Jackson 2 — Suspend ─────────────────────────────────────────────────────

/** Jackson 2 NDJSON OkIO 코루틴 진행 상태 Flow 임포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): Flow<GraphImportProgress> =
    jacksonSuspendAdapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON2, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): Flow<GraphImportProgress> =
    jacksonSuspendAdapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON2, operations, options, listener)

/** Jackson 2 NDJSON OkIO 코루틴 await 임포트 (완료 보고서) */
@Suppress("UnusedReceiverParameter")
suspend fun Jackson2NdJsonBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport =
    jacksonSuspendAdapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON2, operations, options)

@Suppress("UnusedReceiverParameter")
suspend fun Jackson2NdJsonBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport =
    jacksonSuspendAdapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON2, operations, options, listener)

/** Jackson 2 NDJSON OkIO 코루틴 진행 상태 Flow 익스포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson2NdJsonBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): Flow<GraphExportProgress> =
    jacksonSuspendAdapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options)

fun Jackson2NdJsonBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): Flow<GraphExportProgress> =
    jacksonSuspendAdapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options, listener)

/** Jackson 2 NDJSON OkIO 코루틴 await 익스포트 (완료 보고서) */
@Suppress("UnusedReceiverParameter")
suspend fun Jackson2NdJsonBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport =
    jacksonSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options)

@Suppress("UnusedReceiverParameter")
suspend fun Jackson2NdJsonBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport =
    jacksonSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options, listener)

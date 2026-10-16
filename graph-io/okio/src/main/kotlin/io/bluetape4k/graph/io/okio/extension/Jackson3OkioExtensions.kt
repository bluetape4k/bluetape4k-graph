package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
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


// ─── Jackson 3 — Sync ─────────────────────────────────────────────────────────

/**
 * Jackson 3 NDJSON 포맷으로 그래프를 OkIO 소스에서 임포트한다.
 */
fun Jackson3NdJsonBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    return source.openSource().use { bs ->
        this.importGraph(bs.asSource(true), operations, options)
    }
}

fun Jackson3NdJsonBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport {
    return source.openSource().use { bs ->
        this.importGraph(
            bs.asSource(true),
            operations,
            options,
            listener,
        )
    }
}

/** Jackson 3 NDJSON 포맷(Gzip 압축)으로 OkIO 소스에서 임포트한다. */
fun Jackson3NdJsonBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    return source.openGzipSource().use { bs ->
        this.importGraph(bs.asSource(true), operations, options)
    }
}

fun Jackson3NdJsonBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport {
    return source.openGzipSource().use { bs ->
        this.importGraph(
            bs.asSource(true),
            operations,
            options,
            listener,
        )
    }
}


/** 그래프를 Jackson 3 NDJSON 포맷으로 OkIO 싱크에 익스포트한다. */
fun Jackson3NdJsonBulkExporter.exportGraph(
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

fun Jackson3NdJsonBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport {
    return sink.openSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(
                os.asSink(closeOutput = false),
                operations,
                options,
                listener,
            )
        }
    }
}

/** 그래프를 Jackson 3 NDJSON 포맷(Gzip 압축)으로 OkIO 싱크에 익스포트한다. */
fun Jackson3NdJsonBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    return sink.openGzipSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(os.asSink(closeOutput = false), operations, options)
        }
    }
}

fun Jackson3NdJsonBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport {
    return sink.openGzipSink().use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(
                os.asSink(closeOutput = false),
                operations,
                options,
                listener,
            )
        }
    }
}

// ─── Jackson 3 — VirtualThread ────────────────────────────────────────────────

/** Jackson 3 NDJSON OkIO Virtual Thread 비동기 임포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): CompletableFuture<GraphImportReport> =
    jacksonVtAdapter.importGraphAsync(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): CompletableFuture<GraphImportReport> =
    jacksonVtAdapter.importGraphAsync(source, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

/** Jackson 3 NDJSON OkIO Virtual Thread 비동기 익스포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): CompletableFuture<GraphExportReport> =
    jacksonVtAdapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): CompletableFuture<GraphExportReport> =
    jacksonVtAdapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

// ─── Jackson 3 — Suspend ─────────────────────────────────────────────────────

/** Jackson 3 NDJSON OkIO 코루틴 진행 상태 Flow 임포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): Flow<GraphImportProgress> =
    jacksonSuspendAdapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): Flow<GraphImportProgress> =
    jacksonSuspendAdapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

/** Jackson 3 NDJSON OkIO 코루틴 await 임포트 */
@Suppress("UnusedReceiverParameter")
suspend fun Jackson3NdJsonBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport =
    jacksonSuspendAdapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

@Suppress("UnusedReceiverParameter")
suspend fun Jackson3NdJsonBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport =
    jacksonSuspendAdapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

/** Jackson 3 NDJSON OkIO 코루틴 진행 상태 Flow 익스포트 */
@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): Flow<GraphExportProgress> =
    jacksonSuspendAdapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

@Suppress("UnusedReceiverParameter")
fun Jackson3NdJsonBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): Flow<GraphExportProgress> =
    jacksonSuspendAdapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

/** Jackson 3 NDJSON OkIO 코루틴 await 익스포트 */
@Suppress("UnusedReceiverParameter")
suspend fun Jackson3NdJsonBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport =
    jacksonSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

@Suppress("UnusedReceiverParameter")
suspend fun Jackson3NdJsonBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport =
    jacksonSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options, listener)

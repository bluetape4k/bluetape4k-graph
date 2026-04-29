package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
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

// 두 어댑터 모두 상태 없는 싱글턴 — Jackson2/3가 공유해도 무관
private val jacksonVtAdapter = VirtualThreadGraphIoOkioBulkAdapter()
private val jacksonSuspendAdapter = SuspendGraphIoOkioBulkAdapter()

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
    return GraphIoOkioPaths.openSource(source).use { bs ->
        bs.toInputStream().use { is_ ->
            this.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
        }
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
    return GraphIoOkioPaths.openGzipSource(source).use { bs ->
        bs.toInputStream().use { is_ ->
            this.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
        }
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
    return GraphIoOkioPaths.openSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
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
    return GraphIoOkioPaths.openGzipSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
        }
    }
}

// ─── Jackson 2 — VirtualThread ────────────────────────────────────────────────

/** Jackson 2 NDJSON OkIO Virtual Thread 비동기 임포트 */
fun Jackson2NdJsonBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): CompletableFuture<GraphImportReport> =
    jacksonVtAdapter.importGraphAsync(source, GraphIoFormat.NDJSON_JACKSON2, operations, options)

/** Jackson 2 NDJSON OkIO Virtual Thread 비동기 익스포트 */
fun Jackson2NdJsonBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): CompletableFuture<GraphExportReport> =
    jacksonVtAdapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options)

// ─── Jackson 2 — Suspend ─────────────────────────────────────────────────────

/** Jackson 2 NDJSON OkIO 코루틴 진행 상태 Flow 임포트 */
fun Jackson2NdJsonBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): Flow<GraphImportProgress> =
    jacksonSuspendAdapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON2, operations, options)

/** Jackson 2 NDJSON OkIO 코루틴 await 임포트 (완료 보고서) */
suspend fun Jackson2NdJsonBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport =
    jacksonSuspendAdapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON2, operations, options)

/** Jackson 2 NDJSON OkIO 코루틴 진행 상태 Flow 익스포트 */
fun Jackson2NdJsonBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): Flow<GraphExportProgress> =
    jacksonSuspendAdapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options)

/** Jackson 2 NDJSON OkIO 코루틴 await 익스포트 (완료 보고서) */
suspend fun Jackson2NdJsonBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport =
    jacksonSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON2, operations, options)

// ─── Jackson 3 — Sync ─────────────────────────────────────────────────────────

/**
 * Jackson 3 NDJSON 포맷으로 그래프를 OkIO 소스에서 임포트한다.
 */
fun Jackson3NdJsonBulkImporter.importGraph(
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

/** Jackson 3 NDJSON 포맷(Gzip 압축)으로 OkIO 소스에서 임포트한다. */
fun Jackson3NdJsonBulkImporter.importGraphGzip(
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

/** 그래프를 Jackson 3 NDJSON 포맷으로 OkIO 싱크에 익스포트한다. */
fun Jackson3NdJsonBulkExporter.exportGraph(
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

/** 그래프를 Jackson 3 NDJSON 포맷(Gzip 압축)으로 OkIO 싱크에 익스포트한다. */
fun Jackson3NdJsonBulkExporter.exportGraphGzip(
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

// ─── Jackson 3 — VirtualThread ────────────────────────────────────────────────

/** Jackson 3 NDJSON OkIO Virtual Thread 비동기 임포트 */
fun Jackson3NdJsonBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): CompletableFuture<GraphImportReport> =
    jacksonVtAdapter.importGraphAsync(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

/** Jackson 3 NDJSON OkIO Virtual Thread 비동기 익스포트 */
fun Jackson3NdJsonBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): CompletableFuture<GraphExportReport> =
    jacksonVtAdapter.exportGraphAsync(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

// ─── Jackson 3 — Suspend ─────────────────────────────────────────────────────

/** Jackson 3 NDJSON OkIO 코루틴 진행 상태 Flow 임포트 */
fun Jackson3NdJsonBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): Flow<GraphImportProgress> =
    jacksonSuspendAdapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

/** Jackson 3 NDJSON OkIO 코루틴 await 임포트 */
suspend fun Jackson3NdJsonBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport =
    jacksonSuspendAdapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

/** Jackson 3 NDJSON OkIO 코루틴 진행 상태 Flow 익스포트 */
fun Jackson3NdJsonBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): Flow<GraphExportProgress> =
    jacksonSuspendAdapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

/** Jackson 3 NDJSON OkIO 코루틴 await 익스포트 */
suspend fun Jackson3NdJsonBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport =
    jacksonSuspendAdapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

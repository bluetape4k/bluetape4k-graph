package io.bluetape4k.graph.io.okio.extension

import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.okio.asSink
import io.bluetape4k.graph.io.okio.asSource
import io.bluetape4k.graph.io.okio.coroutines.SuspendGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.okio.openGzipSink
import io.bluetape4k.graph.io.okio.openGzipSource
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
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.repository.GraphOperations
import kotlinx.coroutines.flow.Flow
import okio.Path.Companion.toPath
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

// ─── Sync ─────────────────────────────────────────────────────────────────────

/**
 * CSV 포맷으로 그래프를 OkIO 소스에서 임포트한다.
 *
 * CSV 는 정점/간선 파일 분리 포맷이므로 [OkioGraphImportSource.PathSource] 만 지원한다.
 * `{stem}_vertices.csv` + `{stem}_edges.csv` 파일 쌍에서 읽는다.
 * 스트림 기반 소스는 [CsvGraphBulkImporter] 를 직접 사용한다.
 *
 * @param source 파일 경로 기반 OkIO 임포트 소스 ([OkioGraphImportSource.PathSource])
 * @param operations 임포트 대상 그래프 API
 * @param options 임포트 옵션
 */
fun CsvGraphBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    val csvSource = source.asCsvImportSource()
    return this.importGraph(csvSource, operations, options)
}

/** CSV 동기 임포트의 backend-neutral 진행 listener 오버로드. */
fun CsvGraphBulkImporter.importGraph(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport {
    return this.importGraph(source.asCsvImportSource(), operations, options, listener)
}

/**
 * CSV 포맷으로 그래프를 OkIO 소스(Gzip 압축)에서 임포트한다.
 *
 * `{stem}_vertices.csv.gz` + `{stem}_edges.csv.gz` 파일 쌍에서 스트리밍으로 읽는다.
 * [OkioGraphImportSource.PathSource] 만 지원한다.
 *
 * @param source Gzip 압축 CSV 파일 경로 기반 OkIO 임포트 소스
 * @param operations 임포트 대상 그래프 API
 * @param options 임포트 옵션
 * @return 임포트 결과 보고서
 */
fun CsvGraphBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport {
    require(source is OkioGraphImportSource.PathSource) {
        "importGraphGzip 는 PathSource 만 지원합니다."
    }
    val stem = source.path.toString().removeSuffix(".csv.gz").removeSuffix(".csv")
    val verticesSource = OkioGraphImportSource.PathSource("${stem}_vertices.csv.gz".toPath(), source.fileSystem)
    val edgesSource = OkioGraphImportSource.PathSource("${stem}_edges.csv.gz".toPath(), source.fileSystem)
    return verticesSource.openGzipSource().use { vbs ->
        edgesSource.openGzipSource().use { ebs ->
            val csvSource = CsvGraphImportSource(
                vertices = vbs.asSource(false),
                edges = ebs.asSource(false)
            )
            this.importGraph(csvSource, operations, options)
        }
    }
}

/** Gzip CSV 동기 임포트의 backend-neutral 진행 listener 오버로드. */
fun CsvGraphBulkImporter.importGraphGzip(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport {
    require(source is OkioGraphImportSource.PathSource) {
        "importGraphGzip 는 PathSource 만 지원합니다."
    }
    val stem = source.path.toString().removeSuffix(".csv.gz").removeSuffix(".csv")
    val verticesSource = OkioGraphImportSource.PathSource("${stem}_vertices.csv.gz".toPath(), source.fileSystem)
    val edgesSource = OkioGraphImportSource.PathSource("${stem}_edges.csv.gz".toPath(), source.fileSystem)
    return verticesSource.openGzipSource().use { vbs ->
        edgesSource.openGzipSource().use { ebs ->
            val csvSource = CsvGraphImportSource(
                vertices = vbs.asSource(false),
                edges = ebs.asSource(false)
            )
            this.importGraph(csvSource, operations, options, listener)
        }
    }
}

/**
 * 그래프를 CSV 포맷으로 OkIO 싱크에 익스포트한다.
 *
 * [OkioGraphExportSink.PathSink] 만 지원한다. `{stem}_vertices.csv` + `{stem}_edges.csv` 에 쓴다.
 */
fun CsvGraphBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    val csvSink = sink.asCsvExportSink()
    return this.exportGraph(csvSink, operations, options)
}

/** CSV 동기 익스포트의 backend-neutral 진행 listener 오버로드. */
fun CsvGraphBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport {
    return this.exportGraph(sink.asCsvExportSink(), operations, options, listener)
}

/**
 * 그래프를 CSV 포맷(Gzip 압축)으로 OkIO 싱크에 익스포트한다.
 *
 * `{stem}_vertices.csv.gz` + `{stem}_edges.csv.gz` 에 단일 패스로 쓴다.
 * [OkioGraphExportSink.PathSink] 만 지원한다.
 *
 * @param sink Gzip 압축 CSV 파일 경로 기반 OkIO 익스포트 싱크
 * @param operations 익스포트 대상 그래프 API
 * @param options 익스포트 옵션
 * @return 익스포트 결과 보고서 (정점/간선 수 모두 정확)
 */
fun CsvGraphBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport {
    require(sink is OkioGraphExportSink.PathSink) {
        "exportGraphGzip 는 PathSink 만 지원합니다."
    }
    val stem = sink.path.toString().removeSuffix(".csv.gz").removeSuffix(".csv")
    val verticesSink = OkioGraphExportSink.PathSink("${stem}_vertices.csv.gz".toPath(), sink.fileSystem)
    val edgesSink = OkioGraphExportSink.PathSink("${stem}_edges.csv.gz".toPath(), sink.fileSystem)

    return verticesSink.openGzipSink().use { vbs ->
        edgesSink.openGzipSink().use { ebs ->
            val csvSink = CsvGraphExportSink(
                vertices = vbs.asSink(false),
                edges = ebs.asSink(false)
            )
            exportGraph(csvSink, operations, options)
        }
    }
}

/** Gzip CSV 동기 익스포트의 backend-neutral 진행 listener 오버로드. */
fun CsvGraphBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport {
    require(sink is OkioGraphExportSink.PathSink) {
        "exportGraphGzip 는 PathSink 만 지원합니다."
    }
    val stem = sink.path.toString().removeSuffix(".csv.gz").removeSuffix(".csv")
    val verticesSink = OkioGraphExportSink.PathSink("${stem}_vertices.csv.gz".toPath(), sink.fileSystem)
    val edgesSink = OkioGraphExportSink.PathSink("${stem}_edges.csv.gz".toPath(), sink.fileSystem)


    return verticesSink.openGzipSink().use { vbs ->
        edgesSink.openGzipSink().use { ebs ->
            val csvSink = CsvGraphExportSink(
                vertices = vbs.asSink(false),
                edges = ebs.asSink(false),
            )
            exportGraph(csvSink, operations, options, listener)
        }
    }
}

// ─── VirtualThread ────────────────────────────────────────────────────────────

private val vtAdapter = VirtualThreadGraphIoOkioBulkAdapter.Default

/**
 * CSV 포맷으로 그래프를 Virtual Thread 비동기 임포트한다.
 *
 * @see importGraph 동기 변형
 */
@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): CompletableFuture<GraphImportReport> =
    vtAdapter.importGraphAsync(source, GraphIoFormat.CSV, operations, options)

@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkImporter.importGraphAsync(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): CompletableFuture<GraphImportReport> =
    vtAdapter.importGraphAsync(source, GraphIoFormat.CSV, operations, options, listener)

/**
 * CSV 포맷으로 그래프를 Virtual Thread 비동기 익스포트한다.
 *
 * @see exportGraph 동기 변형
 */
@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): CompletableFuture<GraphExportReport> =
    vtAdapter.exportGraphAsync(sink, GraphIoFormat.CSV, operations, options)

@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkExporter.exportGraphAsync(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): CompletableFuture<GraphExportReport> =
    vtAdapter.exportGraphAsync(sink, GraphIoFormat.CSV, operations, options, listener)

// ─── Suspend ─────────────────────────────────────────────────────────────────

private val suspendAdapter = SuspendGraphIoOkioBulkAdapter()

/**
 * CSV 포맷으로 그래프를 코루틴 suspend 임포트하고 진행 상태 Flow를 반환한다.
 *
 * @see importGraph 동기 변형
 */
@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): Flow<GraphImportProgress> =
    suspendAdapter.importGraph(source, GraphIoFormat.CSV, operations, options)

@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkImporter.importGraphFlow(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): Flow<GraphImportProgress> =
    suspendAdapter.importGraph(source, GraphIoFormat.CSV, operations, options, listener)

/**
 * CSV 포맷으로 그래프를 코루틴 suspend 임포트하고 완료 보고서를 반환한다.
 *
 * @see importGraph 동기 변형
 */
@Suppress("UnusedReceiverParameter")
suspend fun CsvGraphBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
): GraphImportReport =
    suspendAdapter.importGraphAwait(source, GraphIoFormat.CSV, operations, options)

@Suppress("UnusedReceiverParameter")
suspend fun CsvGraphBulkImporter.importGraphAwait(
    source: OkioGraphImportSource,
    operations: GraphOperations,
    options: GraphImportOptions = GraphImportOptions(),
    listener: GraphIoProgressListener,
): GraphImportReport =
    suspendAdapter.importGraphAwait(source, GraphIoFormat.CSV, operations, options, listener)

/**
 * CSV 포맷으로 그래프를 코루틴 suspend 익스포트하고 진행 상태 Flow를 반환한다.
 *
 * @see exportGraph 동기 변형
 */
@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): Flow<GraphExportProgress> =
    suspendAdapter.exportGraph(sink, GraphIoFormat.CSV, operations, options)

@Suppress("UnusedReceiverParameter")
fun CsvGraphBulkExporter.exportGraphFlow(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): Flow<GraphExportProgress> =
    suspendAdapter.exportGraph(sink, GraphIoFormat.CSV, operations, options, listener)

/**
 * CSV 포맷으로 그래프를 코루틴 suspend 익스포트하고 완료 보고서를 반환한다.
 *
 * @see exportGraph 동기 변형
 */
@Suppress("UnusedReceiverParameter")
suspend fun CsvGraphBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
): GraphExportReport =
    suspendAdapter.exportGraphAwait(sink, GraphIoFormat.CSV, operations, options)

@Suppress("UnusedReceiverParameter")
suspend fun CsvGraphBulkExporter.exportGraphAwait(
    sink: OkioGraphExportSink,
    operations: GraphOperations,
    options: GraphExportOptions = GraphExportOptions(),
    listener: GraphIoProgressListener,
): GraphExportReport =
    suspendAdapter.exportGraphAwait(sink, GraphIoFormat.CSV, operations, options, listener)

// ─── 내부 헬퍼 ─────────────────────────────────────────────────────────────────

private fun OkioGraphImportSource.asCsvImportSource(): CsvGraphImportSource {
    require(this is OkioGraphImportSource.PathSource) {
        "CSV 포맷은 두 파일(vertices/edges)이 필요하므로 PathSource 만 지원합니다."
    }
    require(fileSystem == okio.FileSystem.SYSTEM) {
        "CSV extension 함수는 FileSystem.SYSTEM 만 지원합니다. 제공된 FileSystem: $fileSystem"
    }
    val stem = path.toString().removeSuffix(".csv")
    return CsvGraphImportSource(
        vertices = Paths.get("${stem}_vertices.csv").asSource(),
        edges = Paths.get("${stem}_edges.csv").asSource(),
    )
}

private fun OkioGraphExportSink.asCsvExportSink(): CsvGraphExportSink {
    require(this is OkioGraphExportSink.PathSink) {
        "CSV 포맷은 두 파일(vertices/edges)이 필요하므로 PathSink 만 지원합니다."
    }
    require(fileSystem == okio.FileSystem.SYSTEM) {
        "CSV extension 함수는 FileSystem.SYSTEM 만 지원합니다. 제공된 FileSystem: $fileSystem"
    }
    val stem = path.toString().removeSuffix(".csv")
    return CsvGraphExportSink(
        vertices = Paths.get("${stem}_vertices.csv").asSink(),
        edges = Paths.get("${stem}_edges.csv").asSink(),
    )
}

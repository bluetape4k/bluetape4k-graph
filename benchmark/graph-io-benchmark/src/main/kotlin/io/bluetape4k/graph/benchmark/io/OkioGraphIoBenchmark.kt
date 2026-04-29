package io.bluetape4k.graph.benchmark.io

import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.okio.extension.exportGraph
import io.bluetape4k.graph.io.okio.extension.exportGraphGzip
import io.bluetape4k.graph.io.okio.extension.importGraph
import io.bluetape4k.graph.io.okio.extension.importGraphGzip
import io.bluetape4k.graph.io.okio.virtualthread.VirtualThreadGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import okio.FileSystem
import okio.Path.Companion.toPath
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * OkIO 기반 그래프 I/O 벤치마크.
 *
 * java.io 기반 [BulkGraphIoBenchmark] 와 비교하여 OkIO 세그먼트 스트리밍의
 * 처리량 및 GC 할당량 특성을 측정한다.
 *
 * 실행:
 * ```
 * ./gradlew :graph-io-benchmark:benchmark
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class OkioGraphIoBenchmark {

    private val exportOpts = GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS"))
    private val importOpts = GraphImportOptions(preserveExternalIdProperty = null)

    private val okioExporter = OkioGraphBulkExporter()
    private val okioImporter = OkioGraphBulkImporter()
    private val vtAdapter = VirtualThreadGraphIoOkioBulkAdapter(okioImporter, okioExporter)

    private fun pathSink(dir: java.nio.file.Path, name: String) =
        OkioGraphExportSink.PathSink(dir.resolve(name).toString().toPath(), FileSystem.SYSTEM)

    private fun pathSource(dir: java.nio.file.Path, name: String) =
        OkioGraphImportSource.PathSource(dir.resolve(name).toString().toPath(), FileSystem.SYSTEM)

    // ─── Jackson3 java.io baseline ───────────────────────────────────────────────

    @Benchmark
    fun jackson3JavaIoExport(s: BulkGraphIoBenchmarkState) {
        Jackson3NdJsonBulkExporter().exportGraph(
            GraphExportSink.PathSink(s.tempDir.resolve("g3jio.ndjson")), s.ops, exportOpts
        )
    }

    @Benchmark
    fun jackson3JavaIoImport(s: BulkGraphIoBenchmarkState) {
        jackson3JavaIoExport(s)
        Jackson3NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(s.tempDir.resolve("g3jio.ndjson")), TinkerGraphOperations(), importOpts
        )
    }

    // ─── Jackson3 OkIO ───────────────────────────────────────────────────────────

    @Benchmark
    fun jackson3OkioExport(s: BulkGraphIoBenchmarkState) {
        okioExporter.exportGraph(pathSink(s.tempDir, "g3ok.ndjson"), GraphIoFormat.NDJSON_JACKSON3, s.ops, exportOpts)
    }

    @Benchmark
    fun jackson3OkioImport(s: BulkGraphIoBenchmarkState) {
        jackson3OkioExport(s)
        okioImporter.importGraph(pathSource(s.tempDir, "g3ok.ndjson"), GraphIoFormat.NDJSON_JACKSON3, TinkerGraphOperations(), importOpts)
    }

    @Benchmark
    fun jackson3OkioRoundTrip(s: BulkGraphIoBenchmarkState) = jackson3OkioImport(s)

    // ─── Jackson3 OkIO + GZIP ────────────────────────────────────────────────────

    @Benchmark
    fun jackson3OkioGzipExport(s: BulkGraphIoBenchmarkState) {
        Jackson3NdJsonBulkExporter().exportGraphGzip(pathSink(s.tempDir, "g3ok.ndjson.gz"), s.ops, exportOpts)
    }

    @Benchmark
    fun jackson3OkioGzipImport(s: BulkGraphIoBenchmarkState) {
        jackson3OkioGzipExport(s)
        Jackson3NdJsonBulkImporter().importGraphGzip(pathSource(s.tempDir, "g3ok.ndjson.gz"), TinkerGraphOperations(), importOpts)
    }

    @Benchmark
    fun jackson3OkioGzipRoundTrip(s: BulkGraphIoBenchmarkState) = jackson3OkioGzipImport(s)

    // ─── GraphML java.io baseline ─────────────────────────────────────────────────

    @Benchmark
    fun graphMlJavaIoExport(s: BulkGraphIoBenchmarkState) {
        GraphMlBulkExporter().exportGraph(
            GraphExportSink.PathSink(s.tempDir.resolve("gjio.graphml")), s.ops, exportOpts
        )
    }

    @Benchmark
    fun graphMlJavaIoImport(s: BulkGraphIoBenchmarkState) {
        graphMlJavaIoExport(s)
        GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(s.tempDir.resolve("gjio.graphml")), TinkerGraphOperations(), importOpts
        )
    }

    // ─── GraphML OkIO ─────────────────────────────────────────────────────────────

    @Benchmark
    fun graphMlOkioExport(s: BulkGraphIoBenchmarkState) {
        okioExporter.exportGraph(pathSink(s.tempDir, "gok.graphml"), GraphIoFormat.GRAPHML, s.ops, exportOpts)
    }

    @Benchmark
    fun graphMlOkioImport(s: BulkGraphIoBenchmarkState) {
        graphMlOkioExport(s)
        okioImporter.importGraph(pathSource(s.tempDir, "gok.graphml"), GraphIoFormat.GRAPHML, TinkerGraphOperations(), importOpts)
    }

    @Benchmark
    fun graphMlOkioRoundTrip(s: BulkGraphIoBenchmarkState) = graphMlOkioImport(s)

    // ─── VirtualThread vs Sync ────────────────────────────────────────────────────

    @Benchmark
    fun jackson3VtOkioExport(s: BulkGraphIoBenchmarkState) {
        vtAdapter.exportGraphAsync(pathSink(s.tempDir, "g3vt.ndjson"), GraphIoFormat.NDJSON_JACKSON3, s.ops, exportOpts).get()
    }

    @Benchmark
    fun jackson3VtOkioImport(s: BulkGraphIoBenchmarkState) {
        jackson3VtOkioExport(s)
        vtAdapter.importGraphAsync(pathSource(s.tempDir, "g3vt.ndjson"), GraphIoFormat.NDJSON_JACKSON3, TinkerGraphOperations(), importOpts).get()
    }

    @Benchmark
    fun jackson3VtOkioRoundTrip(s: BulkGraphIoBenchmarkState) = jackson3VtOkioImport(s)
}

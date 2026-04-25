package io.bluetape4k.graph.benchmark.io

import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.csv.CsvGraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.csv.SuspendCsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.SuspendCsvGraphBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlVirtualThreadBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlVirtualThreadBulkImporter
import io.bluetape4k.graph.io.graphml.SuspendGraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.SuspendGraphMlBulkImporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonVirtualThreadBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonVirtualThreadBulkImporter
import io.bluetape4k.graph.io.jackson2.SuspendJackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.SuspendJackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkImporter
import io.bluetape4k.graph.io.jackson3.SuspendJackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.SuspendJackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.runBlocking
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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class BulkGraphIoBenchmark {

    private val exportOpts = GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS"))
    private val importOpts = GraphImportOptions(preserveExternalIdProperty = null)

    // ─────────────────────────── CSV ───────────────────────────

    @Benchmark
    fun csvSyncExport(s: BulkGraphIoBenchmarkState) {
        val sink = CsvGraphExportSink(
            GraphExportSink.PathSink(s.tempDir.resolve("v.csv")),
            GraphExportSink.PathSink(s.tempDir.resolve("e.csv")),
        )
        CsvGraphBulkExporter().exportGraph(sink, s.ops, exportOpts)
    }

    @Benchmark
    fun csvSyncImport(s: BulkGraphIoBenchmarkState) {
        csvSyncExport(s)
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(s.tempDir.resolve("v.csv")),
            GraphImportSource.PathSource(s.tempDir.resolve("e.csv")),
        )
        CsvGraphBulkImporter().importGraph(source, TinkerGraphOperations(), importOpts)
    }

    @Benchmark
    fun csvSyncRoundTrip(s: BulkGraphIoBenchmarkState) = csvSyncImport(s)

    @Benchmark
    fun csvVtExport(s: BulkGraphIoBenchmarkState) {
        val sink = CsvGraphExportSink(
            GraphExportSink.PathSink(s.tempDir.resolve("vvt.csv")),
            GraphExportSink.PathSink(s.tempDir.resolve("evt.csv")),
        )
        CsvGraphVirtualThreadBulkExporter().exportGraphAsync(sink, s.ops, exportOpts).get()
    }

    @Benchmark
    fun csvVtImport(s: BulkGraphIoBenchmarkState) {
        csvVtExport(s)
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(s.tempDir.resolve("vvt.csv")),
            GraphImportSource.PathSource(s.tempDir.resolve("evt.csv")),
        )
        CsvGraphVirtualThreadBulkImporter().importGraphAsync(source, TinkerGraphOperations(), importOpts).get()
    }

    @Benchmark
    fun csvVtRoundTrip(s: BulkGraphIoBenchmarkState) = csvVtImport(s)

    @Benchmark
    fun csvSuspendExport(s: BulkGraphIoBenchmarkState) = runBlocking {
        val sink = CsvGraphExportSink(
            GraphExportSink.PathSink(s.tempDir.resolve("vco.csv")),
            GraphExportSink.PathSink(s.tempDir.resolve("eco.csv")),
        )
        SuspendCsvGraphBulkExporter().exportGraphSuspending(sink, TinkerGraphSuspendOperations(s.ops as TinkerGraphOperations), exportOpts)
    }

    @Benchmark
    fun csvSuspendImport(s: BulkGraphIoBenchmarkState) = runBlocking {
        csvSuspendExport(s)
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(s.tempDir.resolve("vco.csv")),
            GraphImportSource.PathSource(s.tempDir.resolve("eco.csv")),
        )
        SuspendCsvGraphBulkImporter().importGraphSuspending(source, TinkerGraphSuspendOperations(), importOpts)
    }

    @Benchmark
    fun csvSuspendRoundTrip(s: BulkGraphIoBenchmarkState) = csvSuspendImport(s)

    // ─────────────────────────── Jackson2 ───────────────────────────

    @Benchmark
    fun jackson2SyncExport(s: BulkGraphIoBenchmarkState) {
        Jackson2NdJsonBulkExporter().exportGraph(
            GraphExportSink.PathSink(s.tempDir.resolve("g2.ndjson")), s.ops, exportOpts
        )
    }

    @Benchmark
    fun jackson2SyncImport(s: BulkGraphIoBenchmarkState) {
        jackson2SyncExport(s)
        Jackson2NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(s.tempDir.resolve("g2.ndjson")), TinkerGraphOperations(), importOpts
        )
    }

    @Benchmark
    fun jackson2SyncRoundTrip(s: BulkGraphIoBenchmarkState) = jackson2SyncImport(s)

    @Benchmark
    fun jackson2VtExport(s: BulkGraphIoBenchmarkState) {
        Jackson2NdJsonVirtualThreadBulkExporter().exportGraphAsync(
            GraphExportSink.PathSink(s.tempDir.resolve("g2vt.ndjson")), s.ops, exportOpts
        ).get()
    }

    @Benchmark
    fun jackson2VtImport(s: BulkGraphIoBenchmarkState) {
        jackson2VtExport(s)
        Jackson2NdJsonVirtualThreadBulkImporter().importGraphAsync(
            GraphImportSource.PathSource(s.tempDir.resolve("g2vt.ndjson")), TinkerGraphOperations(), importOpts
        ).get()
    }

    @Benchmark
    fun jackson2VtRoundTrip(s: BulkGraphIoBenchmarkState) = jackson2VtImport(s)

    @Benchmark
    fun jackson2SuspendExport(s: BulkGraphIoBenchmarkState) = runBlocking {
        SuspendJackson2NdJsonBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(s.tempDir.resolve("g2co.ndjson")),
            TinkerGraphSuspendOperations(s.ops as TinkerGraphOperations), exportOpts
        )
    }

    @Benchmark
    fun jackson2SuspendImport(s: BulkGraphIoBenchmarkState) = runBlocking {
        jackson2SuspendExport(s)
        SuspendJackson2NdJsonBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(s.tempDir.resolve("g2co.ndjson")),
            TinkerGraphSuspendOperations(), importOpts
        )
    }

    @Benchmark
    fun jackson2SuspendRoundTrip(s: BulkGraphIoBenchmarkState) = jackson2SuspendImport(s)

    // ─────────────────────────── Jackson3 ───────────────────────────

    @Benchmark
    fun jackson3SyncExport(s: BulkGraphIoBenchmarkState) {
        Jackson3NdJsonBulkExporter().exportGraph(
            GraphExportSink.PathSink(s.tempDir.resolve("g3.ndjson")), s.ops, exportOpts
        )
    }

    @Benchmark
    fun jackson3SyncImport(s: BulkGraphIoBenchmarkState) {
        jackson3SyncExport(s)
        Jackson3NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(s.tempDir.resolve("g3.ndjson")), TinkerGraphOperations(), importOpts
        )
    }

    @Benchmark
    fun jackson3SyncRoundTrip(s: BulkGraphIoBenchmarkState) = jackson3SyncImport(s)

    @Benchmark
    fun jackson3VtExport(s: BulkGraphIoBenchmarkState) {
        Jackson3NdJsonVirtualThreadBulkExporter().exportGraphAsync(
            GraphExportSink.PathSink(s.tempDir.resolve("g3vt.ndjson")), s.ops, exportOpts
        ).get()
    }

    @Benchmark
    fun jackson3VtImport(s: BulkGraphIoBenchmarkState) {
        jackson3VtExport(s)
        Jackson3NdJsonVirtualThreadBulkImporter().importGraphAsync(
            GraphImportSource.PathSource(s.tempDir.resolve("g3vt.ndjson")), TinkerGraphOperations(), importOpts
        ).get()
    }

    @Benchmark
    fun jackson3VtRoundTrip(s: BulkGraphIoBenchmarkState) = jackson3VtImport(s)

    @Benchmark
    fun jackson3SuspendExport(s: BulkGraphIoBenchmarkState) = runBlocking {
        SuspendJackson3NdJsonBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(s.tempDir.resolve("g3co.ndjson")),
            TinkerGraphSuspendOperations(s.ops as TinkerGraphOperations), exportOpts
        )
    }

    @Benchmark
    fun jackson3SuspendImport(s: BulkGraphIoBenchmarkState) = runBlocking {
        jackson3SuspendExport(s)
        SuspendJackson3NdJsonBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(s.tempDir.resolve("g3co.ndjson")),
            TinkerGraphSuspendOperations(), importOpts
        )
    }

    @Benchmark
    fun jackson3SuspendRoundTrip(s: BulkGraphIoBenchmarkState) = jackson3SuspendImport(s)

    // ─────────────────────────── GraphML ───────────────────────────

    @Benchmark
    fun graphMlSyncExport(s: BulkGraphIoBenchmarkState) {
        GraphMlBulkExporter().exportGraph(
            GraphExportSink.PathSink(s.tempDir.resolve("g.graphml")), s.ops, exportOpts
        )
    }

    @Benchmark
    fun graphMlSyncImport(s: BulkGraphIoBenchmarkState) {
        graphMlSyncExport(s)
        GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(s.tempDir.resolve("g.graphml")), TinkerGraphOperations(), importOpts
        )
    }

    @Benchmark
    fun graphMlSyncRoundTrip(s: BulkGraphIoBenchmarkState) = graphMlSyncImport(s)

    @Benchmark
    fun graphMlVtExport(s: BulkGraphIoBenchmarkState) {
        GraphMlVirtualThreadBulkExporter().exportGraphAsync(
            GraphExportSink.PathSink(s.tempDir.resolve("gvt.graphml")), s.ops, exportOpts
        ).get()
    }

    @Benchmark
    fun graphMlVtImport(s: BulkGraphIoBenchmarkState) {
        graphMlVtExport(s)
        GraphMlVirtualThreadBulkImporter().importGraphAsync(
            GraphImportSource.PathSource(s.tempDir.resolve("gvt.graphml")), TinkerGraphOperations(), importOpts
        ).get()
    }

    @Benchmark
    fun graphMlVtRoundTrip(s: BulkGraphIoBenchmarkState) = graphMlVtImport(s)

    @Benchmark
    fun graphMlSuspendExport(s: BulkGraphIoBenchmarkState) = runBlocking {
        SuspendGraphMlBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(s.tempDir.resolve("gco.graphml")),
            TinkerGraphSuspendOperations(s.ops as TinkerGraphOperations), exportOpts
        )
    }

    @Benchmark
    fun graphMlSuspendImport(s: BulkGraphIoBenchmarkState) = runBlocking {
        graphMlSuspendExport(s)
        SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(s.tempDir.resolve("gco.graphml")),
            TinkerGraphSuspendOperations(), importOpts
        )
    }

    @Benchmark
    fun graphMlSuspendRoundTrip(s: BulkGraphIoBenchmarkState) = graphMlSuspendImport(s)
}

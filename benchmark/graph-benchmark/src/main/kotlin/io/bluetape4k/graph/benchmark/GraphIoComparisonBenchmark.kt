package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkExporter
import io.bluetape4k.graph.io.okio.OkioGraphBulkImporter
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import okio.FileSystem
import okio.Path.Companion.toPath
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Compares graph-io implementations under the `graph-benchmark` module.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class GraphIoComparisonBenchmark {

    @Benchmark
    fun exportGraph(state: GraphIoComparisonState): Long =
        state.exportGraph()

    @Benchmark
    fun importGraph(state: GraphIoComparisonState): Long {
        state.exportGraph()
        return state.importGraph()
    }

    @Benchmark
    fun roundTrip(state: GraphIoComparisonState): Long {
        state.exportGraph()
        return state.importGraph()
    }
}

@State(Scope.Benchmark)
open class GraphIoComparisonState {

    @Param("csv", "jackson2", "jackson3", "graphml", "okio-jackson3", "okio-graphml")
    lateinit var graphIo: String

    @Param("small", "medium")
    lateinit var sizeName: String

    lateinit var ops: GraphOperations
    lateinit var tempDir: Path

    private val exportOptions = GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS"))
    private val importOptions = GraphImportOptions(preserveExternalIdProperty = null)

    @Setup(Level.Trial)
    fun setup() {
        tempDir = Files.createTempDirectory("graph-benchmark-io")
        ops = TinkerGraphOperations()

        val (vertexCount, edgeCount) = when (sizeName) {
            "small" -> 1_000 to 2_000
            "medium" -> 10_000 to 20_000
            else -> 1_000 to 2_000
        }

        val vertices = (0 until vertexCount).map { index ->
            ops.createVertex("Person", mapOf("name" to "Person-$index", "rank" to index.toLong()))
        }

        val random = Random(42)
        repeat(edgeCount) { index ->
            val from = vertices[random.nextInt(vertices.size)].id
            val to = vertices[random.nextInt(vertices.size)].id
            ops.createEdge(from, to, "KNOWS", mapOf("rank" to index.toLong()))
        }
    }

    fun exportGraph(): Long =
        when (graphIo) {
            "csv" -> {
                CsvGraphBulkExporter().exportGraph(
                    CsvGraphExportSink(
                        GraphExportSink.PathSink(tempDir.resolve("vertices.csv")),
                        GraphExportSink.PathSink(tempDir.resolve("edges.csv")),
                    ),
                    ops,
                    exportOptions,
                )
            }
            "jackson2" -> {
                Jackson2NdJsonBulkExporter().exportGraph(
                    GraphExportSink.PathSink(tempDir.resolve("graph-jackson2.ndjson")),
                    ops,
                    exportOptions,
                )
            }
            "jackson3" -> {
                Jackson3NdJsonBulkExporter().exportGraph(
                    GraphExportSink.PathSink(tempDir.resolve("graph-jackson3.ndjson")),
                    ops,
                    exportOptions,
                )
            }
            "graphml" -> {
                GraphMlBulkExporter().exportGraph(
                    GraphExportSink.PathSink(tempDir.resolve("graph.graphml")),
                    ops,
                    exportOptions,
                )
            }
            "okio-jackson3" -> {
                OkioGraphBulkExporter().exportGraph(
                    okioSink("graph-okio-jackson3.ndjson"),
                    GraphIoFormat.NDJSON_JACKSON3,
                    ops,
                    exportOptions,
                )
            }
            "okio-graphml" -> {
                OkioGraphBulkExporter().exportGraph(
                    okioSink("graph-okio.graphml"),
                    GraphIoFormat.GRAPHML,
                    ops,
                    exportOptions,
                )
            }
            else -> error("Unsupported graph-io benchmark format: $graphIo")
        }.let { it.verticesWritten + it.edgesWritten }

    fun importGraph(): Long {
        val target = TinkerGraphOperations()
        return when (graphIo) {
            "csv" -> {
                CsvGraphBulkImporter().importGraph(
                    CsvGraphImportSource(
                        GraphImportSource.PathSource(tempDir.resolve("vertices.csv")),
                        GraphImportSource.PathSource(tempDir.resolve("edges.csv")),
                    ),
                    target,
                    importOptions,
                )
            }
            "jackson2" -> {
                Jackson2NdJsonBulkImporter().importGraph(
                    GraphImportSource.PathSource(tempDir.resolve("graph-jackson2.ndjson")),
                    target,
                    importOptions,
                )
            }
            "jackson3" -> {
                Jackson3NdJsonBulkImporter().importGraph(
                    GraphImportSource.PathSource(tempDir.resolve("graph-jackson3.ndjson")),
                    target,
                    importOptions,
                )
            }
            "graphml" -> {
                GraphMlBulkImporter().importGraph(
                    GraphImportSource.PathSource(tempDir.resolve("graph.graphml")),
                    target,
                    importOptions,
                )
            }
            "okio-jackson3" -> {
                OkioGraphBulkImporter().importGraph(
                    okioSource("graph-okio-jackson3.ndjson"),
                    GraphIoFormat.NDJSON_JACKSON3,
                    target,
                    importOptions,
                )
            }
            "okio-graphml" -> {
                OkioGraphBulkImporter().importGraph(
                    okioSource("graph-okio.graphml"),
                    GraphIoFormat.GRAPHML,
                    target,
                    importOptions,
                )
            }
            else -> error("Unsupported graph-io benchmark format: $graphIo")
        }.let { it.verticesCreated + it.edgesCreated }
    }

    @TearDown(Level.Trial)
    fun teardown() {
        runCatching { ops.close() }
        runCatching {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun okioSink(fileName: String): OkioGraphExportSink =
        OkioGraphExportSink.PathSink(tempDir.resolve(fileName).toString().toPath(), FileSystem.SYSTEM)

    private fun okioSource(fileName: String): OkioGraphImportSource =
        OkioGraphImportSource.PathSource(tempDir.resolve(fileName).toString().toPath(), FileSystem.SYSTEM)
}

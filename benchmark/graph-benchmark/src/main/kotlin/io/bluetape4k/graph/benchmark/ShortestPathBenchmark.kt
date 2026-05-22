package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.vt.VirtualThreadOperationsAdapter
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup

/**
 * Shortest-path benchmark over a 1 000-node chain graph, comparing sync and virtual-thread execution.
 *
 * ## Behavior / Contract
 * - [setup] builds a linear chain 0→1→2→…→999 with edge label "LINK" and pre-generates
 *   100 forward (from, to) ID pairs using a fixed seed so results are reproducible.
 * - Pairs satisfy `from.index < to.index`; the directed chain has no reverse paths,
 *   so backward pairs would short-circuit and bias the measurement.
 * - [syncShortestPath100Pairs] runs all 100 pairs sequentially on the calling thread.
 * - [vtShortestPath100Pairs] submits each pair to a virtual-thread pool via
 *   [VirtualThreadOperationsAdapter.shortestPathAsync] and blocks until completion.
 * - Both methods return the count of pairs for which a path was found.
 * - [teardown] closes the underlying [TinkerGraphOperations] instance.
 *
 * ## Example
 * ```kotlin
 * val bench = ShortestPathBenchmark()
 * bench.setup()
 * println(bench.syncShortestPath100Pairs())  // e.g. 100
 * bench.teardown()
 * ```
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Benchmark)
open class ShortestPathBenchmark {

    private val pathOptions = PathOptions(maxDepth = 10)

    lateinit var syncOps: TinkerGraphOperations
    lateinit var vtOps: VirtualThreadOperationsAdapter
    lateinit var pairs: List<Pair<GraphElementId, GraphElementId>>

    @Setup
    fun setup() {
        syncOps = TinkerGraphOperations()
        vtOps = VirtualThreadOperationsAdapter(syncOps)

        val ids = (0 until 1_000).map { index ->
            syncOps.createVertex("Node", mapOf("index" to index.toLong())).id
        }

        ids.zipWithNext { from, to ->
            syncOps.createEdge(from, to, "LINK", emptyMap())
        }

        val rng = kotlin.random.Random(seed = 42)
        pairs = (0 until 100).map {
            val i = rng.nextInt(ids.size - 1)
            val j = rng.nextInt(i + 1, ids.size)
            ids[i] to ids[j]
        }
    }

    @TearDown
    fun teardown() {
        syncOps.close()
    }

    @Benchmark
    fun syncShortestPath100Pairs(): Int =
        pairs.count { (from, to) -> syncOps.shortestPath(from, to, pathOptions) != null }

    @Benchmark
    fun vtShortestPath100Pairs(): Int =
        pairs.count { (from, to) -> vtOps.shortestPathAsync(from, to, pathOptions).join() != null }
}

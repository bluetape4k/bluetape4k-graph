package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.vt.VirtualThreadOperationsAdapter
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

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
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
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

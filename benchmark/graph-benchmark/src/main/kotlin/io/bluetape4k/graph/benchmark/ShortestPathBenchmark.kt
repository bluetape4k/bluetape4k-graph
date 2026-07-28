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
 * 1,000-node chain graph 위에서 sync와 virtual-thread execution을 비교하는 shortest-path benchmark이다.
 *
 * ## 동작/계약
 * - [setup]은 edge label "LINK"로 0→1→2→…→999 linear chain을 만들고 미리 생성한다
 *   결과 재현성을 위해 fixed seed로 100개의 forward (from, to) ID pair를 만든다.
 * - Pair는 `from.index < to.index`를 만족한다. Directed chain에는 reverse path가 없으므로
 *   backward pair는 short-circuit되어 측정값을 bias할 수 있다.
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

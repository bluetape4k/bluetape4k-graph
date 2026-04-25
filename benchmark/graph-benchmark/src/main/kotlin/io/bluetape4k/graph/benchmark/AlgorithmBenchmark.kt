package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Sync vs Virtual Thread 그래프 알고리즘 벤치마크.
 *
 * - PageRank
 * - BFS / DFS
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
open class AlgorithmBenchmark : GraphBenchmarkState() {

    private val tinkerOps get() = syncOps as TinkerGraphOperations
    private val vtAlgoOps get() = VirtualThreadAlgorithmAdapter(tinkerOps)

    @Benchmark
    fun syncPageRank(): Int =
        tinkerOps.pageRank(PageRankOptions()).size

    @Benchmark
    fun vtPageRank(): Int =
        vtAlgoOps.pageRankAsync(PageRankOptions()).join().size

    @Benchmark
    fun syncBfs(): Int =
        tinkerOps.bfs(aliceId, BfsDfsOptions()).size

    @Benchmark
    fun vtBfs(): Int =
        vtAlgoOps.bfsAsync(aliceId, BfsDfsOptions()).join().size

    @Benchmark
    fun syncDfs(): Int =
        tinkerOps.dfs(aliceId, BfsDfsOptions()).size

    @Benchmark
    fun vtDfs(): Int =
        vtAlgoOps.dfsAsync(aliceId, BfsDfsOptions()).join().size
}

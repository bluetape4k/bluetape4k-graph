package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.PathOptions
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Warmup

/**
 * Sync vs Virtual Thread 경로 탐색(최단 경로 / 전체 경로) 벤치마크.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
open class TraversalBenchmark : GraphBenchmarkState() {

    private val pathOptions = PathOptions(maxDepth = 4)

    @Benchmark
    fun syncShortestPath(): Boolean =
        syncOps.shortestPath(aliceId, daveId, pathOptions) != null

    @Benchmark
    fun vtShortestPath(): Boolean =
        vtOps.shortestPathAsync(aliceId, daveId, pathOptions).join() != null

    @Benchmark
    fun syncAllPaths(): Int =
        syncOps.allPaths(aliceId, daveId, pathOptions).size

    @Benchmark
    fun vtAllPaths(): Int =
        vtOps.allPathsAsync(aliceId, daveId, pathOptions).join().size
}

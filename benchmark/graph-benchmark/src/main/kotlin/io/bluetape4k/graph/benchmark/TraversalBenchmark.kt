package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.PathOptions
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Sync vs Virtual Thread 경로 탐색(최단 경로 / 전체 경로) 벤치마크.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
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

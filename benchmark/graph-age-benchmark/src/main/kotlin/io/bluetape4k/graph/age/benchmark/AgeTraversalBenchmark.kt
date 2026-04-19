package io.bluetape4k.graph.age.benchmark

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
 * AGE backend 경로 탐색 벤치마크: shortestPath, allPaths.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
open class AgeTraversalBenchmark: AgeBenchmarkState() {

    private val pathOptions = PathOptions(edgeLabel = KNOWS_LABEL, maxDepth = 4)

    @Benchmark
    fun shortestPath(): Boolean =
        ops.shortestPath(aliceId, daveId, pathOptions) != null

    @Benchmark
    fun allPaths(): Int =
        ops.allPaths(aliceId, daveId, pathOptions).size
}

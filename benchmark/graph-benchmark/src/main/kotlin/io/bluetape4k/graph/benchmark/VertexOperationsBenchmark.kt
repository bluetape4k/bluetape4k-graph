package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.NeighborOptions
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Sync vs Virtual Thread 정점 조회 벤치마크.
 *
 * | 구현 | API |
 * |------|-----|
 * | Sync | `syncOps.findVerticesByLabel` |
 * | VirtualThread | `vtOps.findVerticesByLabelAsync().join()` |
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
open class VertexOperationsBenchmark : GraphBenchmarkState() {

    @Benchmark
    fun syncFindVerticesByLabel(): Int =
        syncOps.findVerticesByLabel("Person").size

    @Benchmark
    fun vtFindVerticesByLabel(): Int =
        vtOps.findVerticesByLabelAsync("Person").join().size

    @Benchmark
    fun syncNeighbors(): Int =
        syncOps.neighbors(aliceId, NeighborOptions()).size

    @Benchmark
    fun vtNeighbors(): Int =
        vtOps.neighborsAsync(aliceId, NeighborOptions()).join().size

    @Benchmark
    fun syncFindVertexById(): Boolean =
        syncOps.findVertexById("Person", aliceId) != null

    @Benchmark
    fun vtFindVertexById(): Boolean =
        vtOps.findVertexByIdAsync("Person", aliceId).join() != null
}

package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
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
 * Neighbor-expansion benchmark over a star graph with a chain ring, comparing sync and virtual-thread execution.
 *
 * ## Behavior / Contract
 * - [setup] builds a star graph: one "Hub" vertex connected to 100 "Leaf" vertices via "CONNECTS" edges,
 *   plus a chain of 99 "NEXT" edges linking consecutive leaf vertices.
 * - [syncNeighbors1Hop] / [vtNeighbors1Hop] expand direct (depth-1) neighbors of the hub.
 * - [syncNeighbors3Hop] / [vtNeighbors3Hop] expand up to 3 hops from the hub.
 * - Virtual-thread variants delegate to [VirtualThreadOperationsAdapter.neighborsAsync] and block on the result.
 * - [teardown] closes the underlying [TinkerGraphOperations] instance.
 *
 * ## Example
 * ```kotlin
 * val bench = NeighborsBenchmark()
 * bench.setup()
 * println(bench.syncNeighbors1Hop())  // e.g. 100
 * bench.teardown()
 * ```
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Benchmark)
open class NeighborsBenchmark {

    private val hop1Options = NeighborOptions(maxDepth = 1)
    private val hop3Options = NeighborOptions(maxDepth = 3)

    lateinit var syncOps: TinkerGraphOperations
    lateinit var vtOps: VirtualThreadOperationsAdapter
    var hubId: GraphElementId = GraphElementId("0")

    @Setup
    fun setup() {
        syncOps = TinkerGraphOperations()
        vtOps = VirtualThreadOperationsAdapter(syncOps)

        val hub = syncOps.createVertex("Hub", mapOf("name" to "hub"))
        hubId = hub.id

        val leaves = (0 until 100).map { index ->
            syncOps.createVertex("Leaf", mapOf("index" to index.toLong()))
        }

        leaves.forEach { leaf ->
            syncOps.createEdge(hubId, leaf.id, "CONNECTS", emptyMap())
        }

        leaves.zipWithNext { a, b ->
            syncOps.createEdge(a.id, b.id, "NEXT", emptyMap())
        }
    }

    @TearDown
    fun teardown() {
        syncOps.close()
    }

    @Benchmark
    fun syncNeighbors1Hop(): Int =
        syncOps.neighbors(hubId, hop1Options).size

    @Benchmark
    fun vtNeighbors1Hop(): Int =
        vtOps.neighborsAsync(hubId, hop1Options).join().size

    @Benchmark
    fun syncNeighbors3Hop(): Int =
        syncOps.neighbors(hubId, hop3Options).size

    @Benchmark
    fun vtNeighbors3Hop(): Int =
        vtOps.neighborsAsync(hubId, hop3Options).join().size
}

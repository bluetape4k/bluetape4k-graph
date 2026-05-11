package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.GraphVertex
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

    @Benchmark
    fun createVertices10kLoop(): Int {
        val ops = TinkerGraphOperations()
        try {
            batchVertexRows().forEach { props ->
                ops.createVertex("Person", props)
            }
            return ops.countVertices("Person").toInt()
        } finally {
            ops.close()
        }
    }

    @Benchmark
    fun createVertices10kBatch(): Int {
        val ops = TinkerGraphOperations()
        try {
            return ops.createVertices("Person", batchVertexRows()).size
        } finally {
            ops.close()
        }
    }

    @Benchmark
    fun createEdges10kLoop(): Int {
        val ops = TinkerGraphOperations()
        try {
            val vertices = ops.createVertices("Person", batchVertexRows())
            cycleEdges(vertices).forEach { edge ->
                ops.createEdge(edge.fromId, edge.toId, "KNOWS", edge.properties)
            }
            return ops.findEdgesByLabel("KNOWS").size
        } finally {
            ops.close()
        }
    }

    @Benchmark
    fun createEdges10kBatch(): Int {
        val ops = TinkerGraphOperations()
        try {
            val vertices = ops.createVertices("Person", batchVertexRows())
            return ops.createEdges("KNOWS", cycleEdges(vertices)).size
        } finally {
            ops.close()
        }
    }

    private fun batchVertexRows(): List<Map<String, Any?>> =
        (0 until 10_000).map { index ->
            mapOf("name" to "Person-$index", "rank" to index.toLong())
        }

    private fun cycleEdges(vertices: List<GraphVertex>): List<BatchEdge> =
        vertices.indices.map { index ->
            BatchEdge(
                fromId = vertices[index].id,
                toId = vertices[(index + 1) % vertices.size].id,
                properties = mapOf("rank" to index.toLong()),
            )
        }
}

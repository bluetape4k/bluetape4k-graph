package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Single-insert loop vs batch insert throughput for 10,000 vertices and edges (TinkerGraph).
 *
 * ## Behavior / Contract
 * - Each benchmark method creates a fresh [TinkerGraphOperations] instance and closes it after use.
 * - Vertex rows: 10,000 `Person` nodes with `name` and `rank` properties.
 * - Edge rows: cycle topology (node[i] → node[(i+1) % n]) labelled `KNOWS`.
 * - Loop variants call `createVertex` / `createEdge` one at a time; batch variants call `createVertices` / `createEdges`.
 *
 * ```kotlin
 * // run via Gradle
 * ./gradlew :graph-benchmark:benchmark
 * ```
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Benchmark)
open class BatchInsertBenchmark {

    private fun vertexRows(): List<Map<String, Any?>> =
        (0 until 10_000).map { i -> mapOf("name" to "Person-$i", "rank" to i.toLong()) }

    private fun cycleEdges(vertices: List<GraphVertex>): List<BatchEdge> =
        vertices.indices.map { i ->
            BatchEdge(
                fromId = vertices[i].id,
                toId = vertices[(i + 1) % vertices.size].id,
                properties = mapOf("rank" to i.toLong()),
            )
        }

    @Benchmark
    fun vertexLoopInsert(): Int {
        val ops = TinkerGraphOperations()
        return try {
            vertexRows().forEach { props -> ops.createVertex("Person", props) }
            ops.countVertices("Person").toInt()
        } finally {
            ops.close()
        }
    }

    @Benchmark
    fun vertexBatchInsert(): Int {
        val ops = TinkerGraphOperations()
        return try {
            ops.createVertices("Person", vertexRows()).size
        } finally {
            ops.close()
        }
    }

    @Benchmark
    fun edgeLoopInsert(): Int {
        val ops = TinkerGraphOperations()
        return try {
            val vertices = ops.createVertices("Person", vertexRows())
            cycleEdges(vertices).forEach { e ->
                ops.createEdge(e.fromId, e.toId, "KNOWS", e.properties)
            }
            ops.findEdgesByLabel("KNOWS").size
        } finally {
            ops.close()
        }
    }

    @Benchmark
    fun edgeBatchInsert(): Int {
        val ops = TinkerGraphOperations()
        return try {
            val vertices = ops.createVertices("Person", vertexRows())
            ops.createEdges("KNOWS", cycleEdges(vertices)).size
        } finally {
            ops.close()
        }
    }
}

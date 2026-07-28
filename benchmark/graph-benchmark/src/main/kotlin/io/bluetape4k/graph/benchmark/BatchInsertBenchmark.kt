package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup

/**
 * 10,000개 vertex 및 edge(TinkerGraph)에 대한 single-insert loop와 batch insert throughput을 비교한다.
 *
 * ## 동작/계약
 * - 각 benchmark method는 fresh [TinkerGraphOperations] instance를 생성하고 사용 후 close한다.
 * - Vertex row는 `name`, `rank` property를 가진 10,000개의 `Person` node이다.
 * - Edge row는 `KNOWS` label의 cycle topology(node[i] → node[(i+1) % n])이다.
 * - Loop variant는 `createVertex` / `createEdge`를 하나씩 호출하고, batch variant는 `createVertices` / `createEdges`를 호출한다.
 *
 * ```kotlin
 * // run via Gradle
 * ./gradlew :graph-benchmark:benchmark
 * ```
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
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

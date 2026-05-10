package io.bluetape4k.graph.age.benchmark

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * AGE backend 정점 CRUD + 이웃 조회 벤치마크.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
open class AgeVertexBenchmark: AgeBenchmarkState() {

    @Benchmark
    fun createVertex(): Boolean {
        val v = ops.createVertex(PERSON_LABEL, mapOf("name" to "Tmp"))
        return v.id.value.isNotEmpty()
    }

    @Benchmark
    fun findVerticesByLabel(): Int =
        ops.findVerticesByLabel(PERSON_LABEL).size

    @Benchmark
    fun findVertexById(): Boolean =
        ops.findVertexById(PERSON_LABEL, aliceId) != null

    @Benchmark
    fun neighbors(): Int =
        ops.neighbors(aliceId, neighborOptions).size

    @Benchmark
    fun createEdge(): Boolean {
        val from = ops.createVertex(PERSON_LABEL, mapOf("name" to "Src"))
        val to = ops.createVertex(PERSON_LABEL, mapOf("name" to "Dst"))
        val edge = ops.createEdge(from.id, to.id, KNOWS_LABEL, mapOf("since" to 2024L))
        return edge.id.value.isNotEmpty()
    }

    @Benchmark
    fun createVertices10kLoop(): Int {
        resetGraph()
        batchVertexRows().forEach { props ->
            ops.createVertex(PERSON_LABEL, props)
        }
        return ops.findVerticesByLabel(PERSON_LABEL).size
    }

    @Benchmark
    fun createVertices10kBatch(): Int {
        resetGraph()
        return ops.createVertices(PERSON_LABEL, batchVertexRows()).size
    }

    @Benchmark
    fun createEdges10kLoop(): Int {
        resetGraph()
        val vertices = ops.createVertices(PERSON_LABEL, batchVertexRows())
        cycleEdges(vertices).forEach { edge ->
            ops.createEdge(edge.fromId, edge.toId, KNOWS_LABEL, edge.properties)
        }
        return ops.findEdgesByLabel(KNOWS_LABEL).size
    }

    @Benchmark
    fun createEdges10kBatch(): Int {
        resetGraph()
        val vertices = ops.createVertices(PERSON_LABEL, batchVertexRows())
        return ops.createEdges(KNOWS_LABEL, cycleEdges(vertices)).size
    }

    private fun resetGraph() {
        if (ops.graphExists(GRAPH_NAME)) {
            ops.dropGraph(GRAPH_NAME)
        }
        ops.createGraph(GRAPH_NAME)
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

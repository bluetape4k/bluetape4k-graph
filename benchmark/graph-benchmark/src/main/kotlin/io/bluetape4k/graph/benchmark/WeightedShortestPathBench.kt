package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup

/**
 * Deterministic TinkerGraph dataset 위의 weighted shortest-path benchmark이다.
 *
 * ## 동작/계약
 * - [setup]은 `vertexCount`개의 vertex가 있는 directed weighted graph를 build한다.
 * - 모든 vertex는 cost `1.0`으로 다음 neighbor에 연결되어 canonical shortest path가 deterministic해진다.
 * - Additional forward edge는 canonical path보다 저렴한 shortcut을 만들지 않으면서 sparse density를 추가한다.
 * - [dijkstra]는 `PathOptions.weightProperty`와 함께 [TinkerGraphOperations.shortestPath]를 호출한다.
 * - [aStar] calls [TinkerGraphOperations.aStarPath] with a zero heuristic, so it is admissible and directly
 *   comparable to Dijkstra over the same weighted graph.
 *
 * ## Example
 * ```kotlin
 * val bench = WeightedShortestPathBench()
 * bench.vertexCount = 100
 * bench.setup()
 * println(bench.dijkstra())
 * println(bench.aStar())
 * bench.teardown()
 * ```
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Benchmark)
open class WeightedShortestPathBench {

    @Param("100", "1000", "10000")
    var vertexCount: Int = 0

    lateinit var ops: TinkerGraphOperations
    var startId: GraphElementId = GraphElementId("0")
    var endId: GraphElementId = GraphElementId("0")

    private val pathOptions = PathOptions(
        edgeLabel = EDGE_LABEL,
        weightProperty = COST_PROPERTY,
        maxVisited = 100_000,
    )

    @Setup
    fun setup() {
        require(vertexCount > 1) { "vertexCount must be greater than 1" }

        ops = TinkerGraphOperations()
        val ids = (0 until vertexCount).map { index ->
            ops.createVertex(
                label = "WeightedNode",
                properties = mapOf("index" to index.toLong()),
            ).id
        }

        startId = ids.first()
        endId = ids.last()

        createWeightedEdges(ids)
    }

    @TearDown
    fun teardown() {
        ops.close()
    }

    @Benchmark
    fun dijkstra(): Double =
        requireNotNull(ops.shortestPath(startId, endId, pathOptions)).totalWeight

    @Benchmark
    fun aStar(): Double =
        requireNotNull(ops.aStarPath(startId, endId, pathOptions) { 0.0 }).totalWeight

    private fun createWeightedEdges(ids: List<GraphElementId>) {
        ids.zipWithNext { from, to ->
            ops.createEdge(from, to, EDGE_LABEL, mapOf(COST_PROPERTY to 1.0))
        }

        createSkipEdges(ids, span = 5, extraCost = 0.25)
        createSkipEdges(ids, span = 17, extraCost = 0.50)
    }

    private fun createSkipEdges(ids: List<GraphElementId>, span: Int, extraCost: Double) {
        for (index in 0 until ids.size - span) {
            ops.createEdge(
                fromId = ids[index],
                toId = ids[index + span],
                label = EDGE_LABEL,
                properties = mapOf(COST_PROPERTY to span.toDouble() + extraCost),
            )
        }
    }

    private companion object {
        const val EDGE_LABEL = "ROUTE"
        const val COST_PROPERTY = "cost"
    }
}

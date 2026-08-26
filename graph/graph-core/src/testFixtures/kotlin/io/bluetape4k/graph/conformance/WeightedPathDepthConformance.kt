package io.bluetape4k.graph.conformance

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNear
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.vt.asVirtualThread

/**
 * Shared TCK for weighted path depth semantics across sync, suspend, and virtual-thread APIs.
 *
 * The fixture deliberately uses only [GraphOperations] contracts so each backend can execute the
 * same boundary cases without importing another backend implementation.
 */
object WeightedPathDepthConformance {

    /** Verifies Dijkstra and A* depth boundaries on a synchronous backend and its VT facade. */
    fun assertSyncAndVirtual(ops: GraphOperations) {
        val graph = createSyncGraph(ops)
        assertPathBoundaries(
            dijkstra = { options -> ops.shortestPath(graph.from.id, graph.to.id, options) },
            aStar = { options -> ops.aStarPath(graph.from.id, graph.to.id, options) { _ -> 0.0 } },
        )

        val virtual = ops.asVirtualThread()
        assertPathBoundaries(
            dijkstra = { options -> virtual.shortestPathAsync(graph.from.id, graph.to.id, options).join() },
            aStar = { options ->
                virtual.aStarPathAsync(graph.from.id, graph.to.id, options) { _ -> 0.0 }.join()
            },
        )
    }

    /** Verifies Dijkstra and A* depth boundaries on a coroutine backend. */
    suspend fun assertSuspend(ops: GraphSuspendOperations) {
        val graph = createSuspendGraph(ops)
        val oneHop = PathOptions(weightProperty = "cost", edgeLabel = "ROAD", maxDepth = 1)
        val twoHops = oneHop.copy(maxDepth = 2)
        val zeroHops = oneHop.copy(maxDepth = 0)

        assertPath(ops.shortestPath(graph.from.id, graph.to.id, oneHop), expectedLength = 1, expectedWeight = 5.0)
        assertPath(ops.aStarPath(graph.from.id, graph.to.id, oneHop) { _ -> 0.0 }, 1, 5.0)
        assertPath(ops.shortestPath(graph.from.id, graph.to.id, twoHops), expectedLength = 2, expectedWeight = 3.0)
        assertPath(ops.aStarPath(graph.from.id, graph.to.id, twoHops) { _ -> 0.0 }, 2, 3.0)
        ops.shortestPath(graph.from.id, graph.to.id, zeroHops).shouldBeNull()
        ops.aStarPath(graph.from.id, graph.to.id, zeroHops) { _ -> 0.0 }.shouldBeNull()
    }

    private fun assertPathBoundaries(
        dijkstra: (PathOptions) -> GraphPath?,
        aStar: (PathOptions) -> GraphPath?,
    ) {
        val oneHop = PathOptions(weightProperty = "cost", edgeLabel = "ROAD", maxDepth = 1)
        val twoHops = oneHop.copy(maxDepth = 2)
        val zeroHops = oneHop.copy(maxDepth = 0)

        assertPath(dijkstra(oneHop), expectedLength = 1, expectedWeight = 5.0)
        assertPath(aStar(oneHop), expectedLength = 1, expectedWeight = 5.0)
        assertPath(dijkstra(twoHops), expectedLength = 2, expectedWeight = 3.0)
        assertPath(aStar(twoHops), expectedLength = 2, expectedWeight = 3.0)

        dijkstra(zeroHops).shouldBeNull()
        aStar(zeroHops).shouldBeNull()
    }

    private fun assertPath(path: GraphPath?, expectedLength: Int, expectedWeight: Double) {
        val actual = path.shouldNotBeNull()
        actual.length shouldBeEqualTo expectedLength
        actual.totalWeight.shouldBeNear(expectedWeight, 0.001)
    }

    private fun createSyncGraph(ops: GraphOperations): WeightedGraph {
        val from = ops.createVertex("City", mapOf("name" to "A"))
        val middle = ops.createVertex("City", mapOf("name" to "B"))
        val to = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(from.id, middle.id, "ROAD", mapOf("cost" to 1.0))
        ops.createEdge(middle.id, to.id, "ROAD", mapOf("cost" to 2.0))
        ops.createEdge(from.id, to.id, "ROAD", mapOf("cost" to 5.0))
        return WeightedGraph(from, middle, to)
    }

    private suspend fun createSuspendGraph(ops: GraphSuspendOperations): WeightedGraph {
        val from = ops.createVertex("City", mapOf("name" to "A"))
        val middle = ops.createVertex("City", mapOf("name" to "B"))
        val to = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(from.id, middle.id, "ROAD", mapOf("cost" to 1.0))
        ops.createEdge(middle.id, to.id, "ROAD", mapOf("cost" to 2.0))
        ops.createEdge(from.id, to.id, "ROAD", mapOf("cost" to 5.0))
        return WeightedGraph(from, middle, to)
    }

    private data class WeightedGraph(
        val from: GraphVertex,
        val middle: GraphVertex,
        val to: GraphVertex,
    )
}

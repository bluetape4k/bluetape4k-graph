package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphEdgeRepository
import io.bluetape4k.graph.repository.GraphOperations

/**
 * JVM weighted shortest-path helper for synchronous backends.
 *
 * Synchronous [GraphOperations] implementations for Neo4j, Memgraph, AGE, TinkerPop, and FalkorDB
 * use it for Dijkstra and A* shortest-path computation.
 *
 * ## Usage pattern
 *
 * **Synchronous backend:**
 * ```kotlin
 * override fun shortestPath(...): GraphPath? =
 *     if (options.weightProperty != null) ShortestPathFallback.dijkstra(this, ...)
 *     else super.shortestPath(...)
 *
 * override fun aStarPath(...): GraphPath? =
 *     ShortestPathFallback.aStar(this, ...)
 * ```
 *
 * **Coroutine backend** (through a sync delegate):
 * ```kotlin
 * override suspend fun shortestPath(...): GraphPath? =
 *     if (options.weightProperty != null) withContext(Dispatchers.IO) {
 *         ShortestPathFallback.dijkstra(syncDelegate, ...)
 *     } else ...
 * ```
 *
 * ## Why this is not an interface default method
 * The traversal algorithms need full [GraphOperations] access for vertex and edge lookup, but
 * `GraphTraversalRepository.this` does not satisfy that type. Backend overrides call this object directly.
 */
object ShortestPathFallback {

    /**
     * Computes a weighted Dijkstra shortest path using [GraphOperations].
     */
    fun dijkstra(
        ops: GraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val runner = DijkstraRunner(
            fetchEdges = { id -> fetchIncident(ops, id, options.edgeLabel, options.direction) },
            fetchVertex = { id -> ops.findVertexById(id) },
        )
        return runner.run(fromId, toId, options)
    }

    /**
     * Computes a weighted A* shortest path using [GraphOperations].
     */
    fun aStar(
        ops: GraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? {
        val runner = AStarRunner(
            fetchEdges = { id -> fetchIncident(ops, id, options.edgeLabel, options.direction) },
            fetchVertex = { id -> ops.findVertexById(id) },
            heuristic = heuristic,
        )
        return runner.run(fromId, toId, options)
    }

    private fun fetchIncident(
        ops: GraphEdgeRepository,
        id: GraphElementId,
        edgeLabel: String?,
        direction: Direction,
    ): List<GraphEdge> = when (direction) {
        Direction.OUTGOING -> ops.findEdgesByStartId(id, edgeLabel)
        Direction.INCOMING -> ops.findEdgesByEndId(id, edgeLabel)
        Direction.BOTH -> (ops.findEdgesByStartId(id, edgeLabel) + ops.findEdgesByEndId(id, edgeLabel))
            .distinctBy { it.id }
            .sortedBy { it.id.value }
    }
}

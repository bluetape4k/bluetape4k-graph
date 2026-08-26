package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import java.util.*

/**
 * Computes a single-source shortest path with Dijkstra's algorithm.
 *
 * ## Design decisions
 * - **Single JVM implementation**: Neo4j, AGE, FalkorDB, and TinkerPop delegate here.
 * - **Deterministic tie-break**: equal-cost vertices are ordered lexicographically by ID.
 * - **maxDepth guard**: weighted paths never exceed [PathOptions.maxDepth] edges.
 * - **maxVisited guard**: protects against unbounded expansion in infinite graphs.
 * - **Direction support**: OUTGOING, INCOMING, and BOTH are all supported.
 *
 * ### Usage
 *
 * ```kotlin
 * val runner = DijkstraRunner(
 *     fetchEdges = { id -> ops.findEdgesByStartId(id) },
 *     fetchVertex = { id -> ops.findVertexById(id) },
 * )
 *
 * val path: GraphPath? = runner.run(
 *     fromId = GraphElementId("A"),
 *     toId = GraphElementId("Z"),
 *     options = PathOptions(
 *         weightProperty = "cost",
 *         missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
 *         maxVisited = 100_000,
 *     ),
 * )
 *
 * path?.let { println("totalCost=${it.totalWeight}, pathLength=${it.steps.size}") }
 * ```
 *
 * @param fetchEdges vertex ID to adjacent edges lookup. `Direction` must be applied before calling.
 * @param fetchVertex vertex ID to [GraphVertex] lookup.
 */
class DijkstraRunner(
    private val fetchEdges: (GraphElementId) -> List<GraphEdge>,
    private val fetchVertex: (GraphElementId) -> GraphVertex?,
) {
    companion object : KLogging()

    /** PriorityQueue entry with deterministic tie-break and no Pair comparator dispatch. */
    private data class DijkstraNode(val cost: Double, val state: WeightedPathState) : Comparable<DijkstraNode> {
        override fun compareTo(other: DijkstraNode): Int {
            val cmp = cost.compareTo(other.cost)
            if (cmp != 0) return cmp
            val idCmp = state.id.value.compareTo(other.state.id.value)
            return if (idCmp != 0) idCmp else state.depth.compareTo(other.state.depth)
        }
    }

    /**
     * Computes the shortest path from [fromId] to [toId] with Dijkstra's algorithm.
	*
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options (`weightProperty`, `missingWeightPolicy`, `maxDepth`, `maxVisited`).
     * @return shortest [GraphPath], or `null` when no path exists.
     * @throws IllegalArgumentException when [PathOptions.weightProperty] is null.
     */
    fun run(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val weightProperty = requireNotNull(options.weightProperty) {
            "PathOptions.weightProperty must be set for Dijkstra"
        }
        val extractor = WeightExtractor(weightProperty, options.missingWeightPolicy)

        // cost + vertexId tie-break keeps traversal deterministic across equal-cost frontiers.
        val pq = PriorityQueue<DijkstraNode>()
        val dist = mutableMapOf<WeightedPathState, Double>()
        val cameFrom = mutableMapOf<WeightedPathState, WeightedPathPredecessor>()
        val startState = WeightedPathState(fromId, 0)

        dist[startState] = 0.0
        pq.add(DijkstraNode(0.0, startState))
        var visited = 0

        outer@ while (pq.isNotEmpty()) {
            val node = pq.poll()
            val cost = node.cost
            val currentState = node.state
            val currentId = currentState.id

            if (cost > (dist[currentState] ?: Double.MAX_VALUE)) continue // stale entry

            if (currentId == toId) {
                log.debug { "Dijkstra found path: $fromId → $toId, cost=$cost, visited=$visited" }
                return reconstructPath(currentState, cameFrom, { fetchVertex(it) }, cost)
            }

            if (++visited > options.maxVisited) {
                log.warn { "Dijkstra maxVisited=${options.maxVisited} reached; no path found from $fromId → $toId" }
                break
            }

            if (currentState.depth >= options.maxDepth) continue

            val currentVertex = fetchVertex(currentId) ?: run {
                log.warn { "Dijkstra: fetchVertex($currentId) returned null mid-traversal — skipping neighbors" }
                continue@outer
            }
            val edges = fetchEdges(currentId)

            for (edge in edges) {
                val neighborId = neighbourId(currentId, edge, options.direction) ?: continue
                val w = extractor.extract(edge) ?: continue // Skip policy

                val newCost = cost + w
                val neighborState = WeightedPathState(neighborId, currentState.depth + 1)
                if (newCost < (dist[neighborState] ?: Double.MAX_VALUE)) {
                    dist[neighborState] = newCost
                    cameFrom[neighborState] = WeightedPathPredecessor(currentState, edge)
                    pq.add(DijkstraNode(newCost, neighborState))
                }
            }
        }

        return null
    }
}

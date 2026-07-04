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
    private data class DijkstraNode(val cost: Double, val id: GraphElementId) : Comparable<DijkstraNode> {
        override fun compareTo(other: DijkstraNode): Int {
            val cmp = cost.compareTo(other.cost)
            return if (cmp != 0) cmp else id.value.compareTo(other.id.value)
        }
    }

    /**
     * Computes the shortest path from [fromId] to [toId] with Dijkstra's algorithm.
	*
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options (`weightProperty`, `missingWeightPolicy`, `maxVisited`).
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
        val dist = mutableMapOf<GraphElementId, Double>()
        val cameFrom = mutableMapOf<GraphElementId, Pair<GraphVertex, GraphEdge>>()

        dist[fromId] = 0.0
        pq.add(DijkstraNode(0.0, fromId))
        var visited = 0

        outer@ while (pq.isNotEmpty()) {
            val (cost, currentId) = pq.poll()

            if (cost > (dist[currentId] ?: Double.MAX_VALUE)) continue // stale entry

            if (currentId == toId) {
                log.debug { "Dijkstra found path: $fromId → $toId, cost=$cost, visited=$visited" }
                return reconstructPath(toId, cameFrom, { fetchVertex(it) }, cost)
            }

            if (++visited > options.maxVisited) {
                log.warn { "Dijkstra maxVisited=${options.maxVisited} reached; no path found from $fromId → $toId" }
                break
            }

            val currentVertex = fetchVertex(currentId) ?: run {
                log.warn { "Dijkstra: fetchVertex($currentId) returned null mid-traversal — skipping neighbors" }
                continue@outer
            }
            val edges = fetchEdges(currentId)

            for (edge in edges) {
                val neighborId = neighbourId(currentId, edge, options.direction) ?: continue
                val w = extractor.extract(edge) ?: continue // Skip policy

                val newCost = cost + w
                if (newCost < (dist[neighborId] ?: Double.MAX_VALUE)) {
                    dist[neighborId] = newCost
                    cameFrom[neighborId] = currentVertex to edge
                    pq.add(DijkstraNode(newCost, neighborId))
                }
            }
        }

        return null
    }
}

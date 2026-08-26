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
 * Computes a heuristic-guided shortest path with the A* algorithm.
 *
 * ## Design decisions
 * - [heuristic] is synchronous only; suspend heuristics are not supported.
 * - Admissible heuristic: `h(n) <= actualCost(n, goal)`. Violating this loses optimality guarantees.
 * - **maxDepth guard**: weighted paths never exceed [PathOptions.maxDepth] edges.
 * - Tie-breaks use the same lexicographic vertex ID ordering as [DijkstraRunner].
 *
 * ### Usage
 *
 * ```kotlin
 * // Coordinate-based Euclidean heuristic, admissible on a 2D grid.
 * val coords: Map<String, Pair<Double, Double>> = ...
 * val goalId = "C"
 *
 * val runner = AStarRunner(
 *     fetchEdges = { id -> ops.findEdgesByStartId(id) },
 *     fetchVertex = { id -> ops.findVertexById(id) },
 *     heuristic = { v ->
 *         val (vx, vy) = coords[v.id.value] ?: (0.0 to 0.0)
 *         val (gx, gy) = coords[goalId] ?: (0.0 to 0.0)
 *         sqrt((vx - gx).pow(2) + (vy - gy).pow(2))
 *     },
 * )
 *
 * val path: GraphPath? = runner.run(
 *     fromId = GraphElementId("A"),
 *     toId = GraphElementId(goalId),
 *     options = PathOptions(weightProperty = "cost", maxVisited = 100_000),
 * )
 * ```
 *
 * @param fetchEdges vertex ID to adjacent edges lookup.
 * @param fetchVertex vertex ID to [GraphVertex] lookup.
 * @param heuristic estimated cost to the target vertex. It must be admissible.
 */
class AStarRunner(
    private val fetchEdges: (GraphElementId) -> List<GraphEdge>,
    private val fetchVertex: (GraphElementId) -> GraphVertex?,
    private val heuristic: (GraphVertex) -> Double,
) {
    companion object : KLogging()

    /** PriorityQueue entry; avoids two boxing operations compared with Triple. */
    private data class AStarNode(val f: Double, val g: Double, val state: WeightedPathState) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int {
            val cmp = f.compareTo(other.f)
            val gCmp = g.compareTo(other.g)
            val idCmp = state.id.value.compareTo(other.state.id.value)
            return when {
                cmp != 0 -> cmp
                gCmp != 0 -> gCmp
                idCmp != 0 -> idCmp
                else -> state.depth.compareTo(other.state.depth)
            }
        }
    }

    /**
     * Computes the shortest path from [fromId] to [toId] with the A* algorithm.
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
            "PathOptions.weightProperty must be set for A*"
        }
        val extractor = WeightExtractor(weightProperty, options.missingWeightPolicy)

        // f = g + h; AStarNode is Comparable, so no Comparator is needed.
        val pq = PriorityQueue<AStarNode>()
        val gScore = mutableMapOf<WeightedPathState, Double>()
        val cameFrom = mutableMapOf<WeightedPathState, WeightedPathPredecessor>()
        val startState = WeightedPathState(fromId, 0)

        gScore[startState] = 0.0
        val startVertex = fetchVertex(fromId) ?: return null
        val h0 = heuristic(startVertex)
        pq.add(AStarNode(h0, 0.0, startState))
        var visited = 0

        outer@ while (pq.isNotEmpty()) {
            val node = pq.poll()
            val g = node.g
            val currentState = node.state
            val currentId = currentState.id

            if (g > (gScore[currentState] ?: Double.MAX_VALUE)) continue // stale

            if (currentId == toId) {
                log.debug { "A* found path: $fromId → $toId, cost=$g, visited=$visited" }
                return reconstructPath(currentState, cameFrom, { fetchVertex(it) }, g)
            }

            if (++visited > options.maxVisited) {
                log.warn { "A* maxVisited=${options.maxVisited} reached; no path found from $fromId → $toId" }
                break
            }

            if (currentState.depth >= options.maxDepth) continue

            val currentVertex = fetchVertex(currentId) ?: run {
                log.warn { "A*: fetchVertex($currentId) returned null mid-traversal — skipping neighbors" }
                continue@outer
            }
            val edges = fetchEdges(currentId)

            for (edge in edges) {
                val neighborId = neighbourId(currentId, edge, options.direction) ?: continue
                val w = extractor.extract(edge) ?: continue

                val tentativeG = g + w
                val neighborState = WeightedPathState(neighborId, currentState.depth + 1)
                if (tentativeG < (gScore[neighborState] ?: Double.MAX_VALUE)) {
                    gScore[neighborState] = tentativeG
                    cameFrom[neighborState] = WeightedPathPredecessor(currentState, edge)

                    val neighbor = fetchVertex(neighborId) ?: run {
                        log.warn { "A*: fetchVertex($neighborId) returned null — neighbor dropped from frontier" }
                        continue
                    }
                    val f = tentativeG + heuristic(neighbor)
                    pq.add(AStarNode(f, tentativeG, neighborState))
                }
            }
        }

        return null
    }
}

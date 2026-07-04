package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions

/**
 * Synchronous graph traversal repository.
 *
 * ```kotlin
 * // One-hop outgoing neighbor traversal.
 * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 *
 * // Shortest path with at most 10 hops.
 * val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
 *
 * // All paths.
 * val paths = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5))
 * ```
 */
interface GraphTraversalRepository {
    /**
     * Finds adjacent neighbor vertices from the start vertex.
	*
     * [NeighborOptions.direction] selects outgoing, incoming, or bidirectional traversal.
     * [NeighborOptions.maxDepth] values of 2 or more include multi-hop neighbors.
     *
     * ```kotlin
     * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
     * val all3hop = ops.neighbors(alice.id, NeighborOptions(maxDepth = 3))
     * ```
     *
     * @param startId vertex ID to start traversal from.
     * @param options traversal options for label filtering, direction, and maximum depth.
     * @return adjacent [GraphVertex] values.
     */
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): List<GraphVertex>

    /**
     * Finds the shortest path between two vertices.
	*
     * Traversal is limited by [PathOptions.maxDepth]. Returns `null` when no path exists
     * or the shortest path exceeds the maximum depth.
     *
     * ```kotlin
     * val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
     * println(path?.length)  // 2 (alice→bob→carol)
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options for label filtering and maximum depth.
     * @return shortest [GraphPath], or `null` when no path exists.
     */
    fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    /**
     * Finds all paths between two vertices.
	*
     * Returns all simple paths up to [PathOptions.maxDepth], or an empty list when no paths exist.
     *
     * ```kotlin
     * val paths = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5))
     * println(paths.size)  // path count
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options for label filtering and maximum depth.
     * @return [GraphPath] values.
     */
    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): List<GraphPath>

    /**
     * Finds a weighted shortest path with the A* algorithm.
	*
     * `options.weightProperty` must be set. [heuristic] must be an admissible synchronous
     * function that estimates cost to the target vertex; suspend heuristics are not supported.
     *
     * ```kotlin
     * val opts = PathOptions(weightProperty = "distance", direction = Direction.OUTGOING)
     * val path = ops.aStarPath(a.id, b.id, opts) { vertex ->
     *     // Admissible heuristic such as Euclidean distance.
     *     euclidean(vertex, goal)
     * }
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options; [PathOptions.weightProperty] is required.
     * @param heuristic admissible estimated cost function to the target.
     * @return weighted shortest [GraphPath], or `null` when no path exists.
     */
    fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath?
}

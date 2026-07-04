package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import kotlinx.coroutines.flow.Flow

/**
 * Coroutine graph traversal repository.
 *
 * Collection-returning operations expose [Flow] so large result sets can stream.
 *
 * ```kotlin
 * runBlocking {
 *     val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
 *     val path    = ops.shortestPath(alice.id, carol.id, PathOptions(maxDepth = 10))
 *     val paths   = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5)).toList()
 * }
 * ```
 *
 * @see GraphTraversalRepository synchronous blocking variant
 */
interface GraphSuspendTraversalRepository {
    /**
     * Finds adjacent neighbor vertices from the start vertex as a [Flow].
     *
     * [NeighborOptions.direction] selects outgoing, incoming, or bidirectional traversal.
     * This method is suitable for streaming large result sets.
     *
     * ```kotlin
     * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
     * ```
     *
     * @param startId vertex ID to start traversal from.
     * @param options traversal options for label filtering, direction, and maximum depth.
     * @return [Flow] of adjacent [GraphVertex] values.
     */
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): Flow<GraphVertex>

    /**
     * Finds the shortest path between two vertices.
     *
     * Traversal is limited by [PathOptions.maxDepth]. Returns `null` when no path exists
     * or the shortest path exceeds the maximum depth.
     *
     * ```kotlin
     * val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
     * println(path?.length)  // 2
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options for label filtering and maximum depth.
     * @return shortest [GraphPath], or `null` when no path exists.
     */
    suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    /**
     * Finds all paths between two vertices as a [Flow].
     *
     * Streams all simple paths up to [PathOptions.maxDepth].
     *
     * ```kotlin
     * val paths = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5)).toList()
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options for label filtering and maximum depth.
     * @return [Flow] of [GraphPath] values.
     */
    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): Flow<GraphPath>

    /**
     * Finds a weighted shortest path with the A* algorithm.
     *
     * `options.weightProperty` must be set. [heuristic] must be a synchronous
     * function; suspend heuristics are not supported.
     *
     * ```kotlin
     * val path = ops.aStarPath(a.id, b.id, PathOptions(weightProperty = "cost")) { vertex ->
     *     estimatedCostToGoal(vertex)
     * }
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options; [PathOptions.weightProperty] is required.
     * @param heuristic admissible estimated cost function to the target.
     * @return weighted shortest [GraphPath], or `null` when no path exists.
     */
    suspend fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath?
}

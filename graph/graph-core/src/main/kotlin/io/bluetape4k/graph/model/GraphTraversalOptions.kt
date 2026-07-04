package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Base sealed class for graph traversal options.
 *
 * ### Usage
 * ```kotlin
 * // Find OUTGOING neighbors one hop away.
 * val neighborOpts = NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING)
 *
 * // Find shortest or all paths up to five hops.
 * val pathOpts = PathOptions(edgeLabel = "KNOWS", maxDepth = 5)
 * ```
 */
sealed class GraphTraversalOptions: Serializable {
    /**
     * Maximum traversal depth. Defaults vary by subclass: [NeighborOptions] uses `1`, [PathOptions] uses `10`.
     *
     * ```kotlin
     * val opts = NeighborOptions(maxDepth = 3)  // Traverse up to 3 hops.
     * ```
     */
    abstract val maxDepth: Int
}

/**
 * Options for `GraphTraversalRepository.neighbors`.
 *
 * ```kotlin
 * val opts = NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
 * val friends = ops.neighbors(alice.id, opts)
 * ```
 *
 * @param edgeLabel Edge label to traverse. `null` traverses all labels.
 * @param direction Traversal direction: `OUTGOING`, `INCOMING`, or `BOTH`.
 * @param maxDepth Maximum traversal depth. Defaults to `1`.
 */
data class NeighborOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 1,
): GraphTraversalOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = NeighborOptions()
    }
}

/**
 * Options for `GraphTraversalRepository.shortestPath` and `GraphTraversalRepository.allPaths`.
 *
 * When [weightProperty] is set, weighted shortest-path search uses Dijkstra/A*.
 * When it is `null` (default), backend-native BFS shortest-path search is used.
 *
 * ```kotlin
 * // Unweighted shortest path (default).
 * val opts = PathOptions(edgeLabel = "KNOWS", maxDepth = 5)
 *
 * // Weighted shortest path (Dijkstra).
 * val opts = PathOptions(
 *     edgeLabel = "ROAD",
 *     weightProperty = "distance",
 *     missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
 *     direction = Direction.BOTH,
 *     maxVisited = 50_000,
 * )
 * ```
 *
 * @param edgeLabel Edge label to traverse. `null` traverses all labels.
 * @param maxDepth Maximum traversal depth. Defaults to `10`.
 * @param weightProperty Edge weight property key. `null` uses unweighted traversal.
 * @param missingWeightPolicy Policy for edges missing [weightProperty]. Defaults to [MissingWeightPolicy.Fail].
 * @param direction Traversal direction. Applies only when [weightProperty] is set. Defaults to [Direction.OUTGOING].
 * @param maxVisited Maximum visited vertices during weighted traversal. Protects against unbounded graphs. Defaults to `100_000`.
 */
data class PathOptions(
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    val weightProperty: String? = null,
    val missingWeightPolicy: MissingWeightPolicy = MissingWeightPolicy.Fail,
    val direction: Direction = Direction.OUTGOING,
    val maxVisited: Int = 100_000,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxVisited > 0) { "maxVisited must be > 0, was $maxVisited" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PathOptions()
    }
}

/**
 * Options for `GraphTraversalRepository.bfs` and `GraphTraversalRepository.dfs`.
 *
 * ### Usage
 * ```kotlin
 * val opts = BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3, maxVertices = 1_000)
 * val visits = ops.bfs(alice.id, opts)
 * ```
 */
data class BfsDfsOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 5,
    val maxVertices: Int = 10_000,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxVertices > 0) { "maxVertices must be > 0, was $maxVertices" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = BfsDfsOptions()
    }
}

/**
 * Options for `GraphTraversalRepository.detectCycles`.
 *
 * @param vertexLabel Vertex label to traverse. `null` traverses all labels.
 * @param edgeLabel Edge label to traverse. `null` traverses all labels.
 * @param maxDepth Maximum traversal depth. Defaults to `10`.
 * @param maxCycles Maximum number of cycles to return. Defaults to `100`.
 */
data class CycleOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    val maxCycles: Int = 100,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxCycles > 0) { "maxCycles must be > 0, was $maxCycles" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = CycleOptions()
    }
}

package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions

/**
 * synchronous graph traversal repository.
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
     * start vertex에서 인접 neighbor vertex를 찾는다.
	*
     * [NeighborOptions.direction] selects outgoing, incoming, or bidirectional traversal.
     * [NeighborOptions.maxDepth] values of 2 or more include multi-hop neighbors.
     *
     * ```kotlin
     * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
     * val all3hop = ops.neighbors(alice.id, NeighborOptions(maxDepth = 3))
     * ```
     *
     * @param startId traversal을 시작할 vertex ID.
     * @param options label filtering, direction, maximum depth를 지정하는 traversal option.
     * @return 인접 [GraphVertex] value 목록.
     */
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): List<GraphVertex>

    /**
     * 두 vertex 사이의 shortest path를 찾는다.
	*
     * traversal은 [PathOptions.maxDepth]로 제한된다. path가 없거나
     * shortest path가 maximum depth를 넘으면 `null`을 반환한다.
     *
     * ```kotlin
     * val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
     * println(path?.length)  // 2 (alice→bob→carol)
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options label filtering과 maximum depth를 지정하는 traversal option.
     * @return shortest [GraphPath]. path가 없으면 `null`.
     */
    fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    /**
     * 두 vertex 사이의 모든 path를 찾는다.
	*
     * [PathOptions.maxDepth]까지의 모든 simple path를 반환한다. path가 없으면 empty list를 반환한다.
     *
     * ```kotlin
     * val paths = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5))
     * println(paths.size)  // path count
     * ```
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options label filtering과 maximum depth를 지정하는 traversal option.
     * @return [GraphPath] values.
     */
    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): List<GraphPath>

    /**
     * A* algorithm으로 weighted shortest path를 찾는다.
	*
     * `options.weightProperty` must be set. [heuristic] must be an admissible synchronous
     * target vertex까지의 cost를 추정하는 function이다. suspend heuristic은 지원하지 않는다.
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
     * @param options traversal option. [PathOptions.weightProperty]가 필요하다.
     * @param heuristic target까지의 admissible estimated cost function.
     * @return weighted shortest [GraphPath]. path가 없으면 `null`.
     */
    fun aStarPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath?
}

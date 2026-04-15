package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions

/**
 * 그래프 순회(Traversal) 저장소 (동기 방식).
 *
 * ```kotlin
 * // 1단계 아웃고잉 이웃 탐색
 * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 *
 * // 최단 경로 (최대 10홉)
 * val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
 *
 * // 모든 경로
 * val paths = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5))
 * ```
 */
interface GraphTraversalRepository {
    /**
     * 시작 정점의 인접 정점(이웃)을 탐색한다.
     *
     * [NeighborOptions.direction]에 따라 나가는 방향, 들어오는 방향, 또는 양방향으로 탐색한다.
     * [NeighborOptions.maxDepth]가 2 이상이면 다단계 이웃까지 탐색한다.
     *
     * @param startId 탐색을 시작할 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 방향, 최대 깊이).
     * @return 인접 [GraphVertex] 목록.
     */
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): List<GraphVertex>

    /**
     * 두 정점 사이의 최단 경로를 찾는다.
     *
     * [PathOptions.maxDepth]까지만 탐색한다.
     * 경로가 없거나 최대 깊이를 초과하면 `null`을 반환한다.
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 최대 깊이).
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     */
    fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    /**
     * 두 정점 사이의 모든 경로를 찾는다.
     *
     * [PathOptions.maxDepth]까지의 모든 단순 경로를 반환한다.
     * 경로가 없으면 빈 목록을 반환한다.
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 최대 깊이).
     * @return [GraphPath] 목록.
     */
    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): List<GraphPath>
}

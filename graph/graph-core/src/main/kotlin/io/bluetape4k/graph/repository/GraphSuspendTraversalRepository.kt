package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 순회(Traversal) 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * ```kotlin
 * runBlocking {
 *     val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
 *     val path    = ops.shortestPath(alice.id, carol.id, PathOptions(maxDepth = 10))
 *     val paths   = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5)).toList()
 * }
 * ```
 *
 * @see GraphTraversalRepository 동기(blocking) 방식
 */
interface GraphSuspendTraversalRepository {
    /**
     * 시작 정점의 인접 정점(이웃)을 Flow로 탐색한다.
     *
     * [NeighborOptions.direction]에 따라 나가는 방향, 들어오는 방향, 또는 양방향으로 탐색한다.
     * 대량 결과 스트리밍에 적합하다.
     *
     * ```kotlin
     * val friends = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
     * ```
     *
     * @param startId 탐색을 시작할 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 방향, 최대 깊이).
     * @return 인접 [GraphVertex] Flow.
     */
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): Flow<GraphVertex>

    /**
     * 두 정점 사이의 최단 경로를 찾는다.
     *
     * [PathOptions.maxDepth]까지만 탐색한다.
     * 경로가 없거나 최대 깊이를 초과하면 `null`을 반환한다.
     *
     * ```kotlin
     * val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
     * println(path?.length)  // 2
     * ```
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 최대 깊이).
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     */
    suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    /**
     * 두 정점 사이의 모든 경로를 Flow로 탐색한다.
     *
     * [PathOptions.maxDepth]까지의 모든 단순 경로를 스트리밍한다.
     *
     * ```kotlin
     * val paths = ops.allPaths(alice.id, carol.id, PathOptions(maxDepth = 5)).toList()
     * ```
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 최대 깊이).
     * @return [GraphPath] Flow.
     */
    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): Flow<GraphPath>
}

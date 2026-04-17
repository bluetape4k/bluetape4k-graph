package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread 기반 그래프 순회(Traversal) 저장소.
 *
 * Java 25 Project Loom 의 Virtual Thread 위에서 동기 [GraphTraversalRepository] 를 실행해
 * `CompletableFuture<T>` 로 결과를 반환한다. Java 코드 또는 CompletableFuture 기반 파이프라인과의
 * 상호운용을 위해 제공된다.
 *
 * Kotlin 코드에서는 [GraphSuspendTraversalRepository] 사용을 권장한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = ops.asVirtualThreadTraversal()
 * val future = vtOps.neighborsAsync(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 * val friends = future.join()
 * ```
 */
interface GraphVirtualThreadTraversalRepository {

    /**
     * 시작 정점의 인접 정점(이웃)을 Virtual Thread 에서 탐색한다.
     *
     * @param startId 탐색을 시작할 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 방향, 최대 깊이).
     * @return 인접 [GraphVertex] 목록을 담은 [CompletableFuture].
     */
    fun neighborsAsync(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): CompletableFuture<List<GraphVertex>>

    /**
     * 두 정점 사이의 최단 경로를 Virtual Thread 에서 찾는다.
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 최대 깊이).
     * @return 최단 [GraphPath] 를 담은 [CompletableFuture]. 경로가 없으면 `null`.
     */
    fun shortestPathAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): CompletableFuture<GraphPath?>

    /**
     * 두 정점 사이의 모든 경로를 Virtual Thread 에서 찾는다.
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (레이블 필터, 최대 깊이).
     * @return [GraphPath] 목록을 담은 [CompletableFuture].
     */
    fun allPathsAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): CompletableFuture<List<GraphPath>>
}

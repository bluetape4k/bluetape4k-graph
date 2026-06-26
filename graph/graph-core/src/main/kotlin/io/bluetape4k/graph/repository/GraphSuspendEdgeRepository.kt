package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 간선(Edge) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * ```kotlin
 * runBlocking {
 *     val edge    = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *     val edges   = ops.findEdgesByLabel("KNOWS").toList()
 *     val deleted = ops.deleteEdge("KNOWS", edge.id)  // true
 * }
 * ```
 *
 * @see GraphEdgeRepository 동기(blocking) 방식
 */
interface GraphSuspendEdgeRepository {
    /**
     * 두 정점 사이에 새 간선을 생성하고 반환한다.
     *
     * ```kotlin
     * val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
     * ```
     *
     * @param fromId 시작 정점 ID.
     * @param toId 종료 정점 ID.
     * @param label 간선 레이블 (예: `"KNOWS"`, `"WORKS_AT"`).
     * @param properties 간선에 저장할 속성 맵. 기본값은 빈 맵.
     * @return 백엔드에서 생성된 [GraphEdge] (ID가 채워진 상태).
     */
    suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): GraphEdge

    /**
     * 같은 레이블의 간선을 여러 개 생성하고 입력 순서와 같은 순서로 반환한다.
     *
     * ## 동작/계약
     *
     * - 빈 입력은 백엔드를 호출하지 않고 `emptyList()`를 반환한다.
     * - 기본 구현은 [createEdge]를 순차 호출하는 호환성 fallback이다.
     * - 기본 구현은 중간 실패 시 앞서 생성된 간선이 남을 수 있다.
     * - 성능 및 all-or-fail 의미가 필요한 프로덕션 백엔드는 이 메서드를 override해야 한다.
     *
     * @param label 간선 레이블.
     * @param edges 간선 endpoint와 속성 목록.
     * @return 백엔드에서 생성된 [GraphEdge] 목록.
     */
    suspend fun createEdges(
        label: String,
        edges: List<BatchEdge>,
    ): List<GraphEdge> {
        GraphBatchValidation.validateEdgeBatch(label, edges)
        if (edges.isEmpty()) return emptyList()
        return edges.map { edge -> createEdge(edge.fromId, edge.toId, label, edge.properties) }
    }

    /**
     * 레이블과 속성 필터로 간선 목록을 스트림으로 조회한다.
     *
     * 대량 데이터에 적합한 [Flow] 기반 조회이다.
     *
     * ```kotlin
     * val edges = ops.findEdgesByLabel("KNOWS").toList()
     * val filtered = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2024)).toList()
     * ```
     *
     * @param label 조회할 간선 레이블.
     * @param filter 속성 이름→값 조건 맵. 빈 맵이면 레이블 전체를 반환.
     * @return 조건에 맞는 [GraphEdge] Flow.
     */
    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphEdge>

    /**
     * Finds edges by label and property filter as a Flow of bounded chunks.
     *
     * The default implementation groups the record Flow from [findEdgesByLabel].
     * Backends with driver cursors or paging APIs can override this method so the
     * backend query itself runs chunk-by-chunk.
     *
     * ```kotlin
     * ops.findEdgesByLabelChunked("KNOWS", chunkSize = 500)
     *     .collect { chunk -> println(chunk.size) }
     * ```
     *
     * @param label edge label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @param chunkSize maximum number of edges per chunk.
     * @return Flow of chunks containing matching [GraphEdge] values.
     */
    fun findEdgesByLabelChunked(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): Flow<List<GraphEdge>> =
        findEdgesByLabel(label, filter).asGraphExportChunks(chunkSize)

    /**
     * 특정 정점에서 출발하는 간선 목록을 스트림으로 조회한다.
     *
     * ```kotlin
     * val outEdges = ops.findEdgesByStartId(alice.id).toList()
     * ```
     *
     * @param startId 시작 정점 ID.
     * @param edgeLabel 간선 레이블 필터. null이면 모든 레이블 반환.
     * @return 해당 정점에서 출발하는 [GraphEdge] Flow.
     */
    fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String? = null): Flow<GraphEdge>

    /**
     * 특정 정점으로 도착하는 간선 목록을 스트림으로 조회한다.
     *
     * ```kotlin
     * val inEdges = ops.findEdgesByEndId(alice.id).toList()
     * ```
     *
     * @param endId 종료 정점 ID.
     * @param edgeLabel 간선 레이블 필터. null이면 모든 레이블 반환.
     * @return 해당 정점으로 도착하는 [GraphEdge] Flow.
     */
    fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String? = null): Flow<GraphEdge>

    /**
     * 간선을 삭제한다.
     *
     * ```kotlin
     * val deleted = ops.deleteEdge("KNOWS", edge.id)  // true
     * ```
     *
     * @param label 간선 레이블.
     * @param id 삭제할 간선 ID.
     * @return 삭제 성공이면 `true`, 해당 ID가 없으면 `false`.
     */
    suspend fun deleteEdge(label: String, id: GraphElementId): Boolean
}

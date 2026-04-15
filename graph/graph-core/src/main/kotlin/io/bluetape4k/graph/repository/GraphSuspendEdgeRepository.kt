package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 간선(Edge) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * @see GraphEdgeRepository 동기(blocking) 방식
 */
interface GraphSuspendEdgeRepository {
    /**
     * 두 정점 사이에 새 간선을 생성하고 반환한다.
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
     * 레이블과 속성 필터로 간선 목록을 스트림으로 조회한다.
     *
     * 대량 데이터에 적합한 [Flow] 기반 조회이다.
     *
     * @param label 조회할 간선 레이블.
     * @param filter 속성 이름→값 조건 맵. 빈 맵이면 레이블 전체를 반환.
     * @return 조건에 맞는 [GraphEdge] Flow.
     */
    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphEdge>

    /**
     * 간선을 삭제한다.
     *
     * @param label 간선 레이블.
     * @param id 삭제할 간선 ID.
     * @return 삭제 성공이면 `true`, 해당 ID가 없으면 `false`.
     */
    suspend fun deleteEdge(label: String, id: GraphElementId): Boolean
}

package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 정점(Vertex) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * ```kotlin
 * runBlocking {
 *     val vertex = ops.createVertex("Person", mapOf("name" to "Alice"))
 *     val found  = ops.findVertexById("Person", vertex.id)
 *     val list   = ops.findVerticesByLabel("Person").toList()
 *     val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 31))
 *     ops.deleteVertex("Person", vertex.id)
 * }
 * ```
 *
 * @see GraphVertexRepository 동기(blocking) 방식
 */
interface GraphSuspendVertexRepository {
    /**
     * 새 정점을 생성하고 반환한다.
     *
     * ```kotlin
     * val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
     * ```
     *
     * @param label 정점 레이블.
     * @param properties 정점에 저장할 속성 맵. 기본값은 빈 맵.
     * @return 백엔드에서 생성된 [GraphVertex] (ID가 채워진 상태).
     */
    suspend fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex

    /**
     * ID로 단일 정점을 조회한다.
     *
     * ```kotlin
     * val found = ops.findVertexById("Person", vertex.id)  // non-null
     * ```
     *
     * @param label 정점 레이블.
     * @param id 조회할 정점 ID.
     * @return 존재하면 [GraphVertex], 없으면 `null`.
     */
    suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex?

    /**
     * 레이블과 속성 필터로 정점 목록을 스트림으로 조회한다.
     *
     * 대량 데이터에 적합한 [Flow] 기반 조회이다.
     *
     * ```kotlin
     * val all  = ops.findVerticesByLabel("Person").toList()
     * val aged = ops.findVerticesByLabel("Person", mapOf("age" to 30)).toList()
     * ```
     *
     * @param label 조회할 정점 레이블.
     * @param filter 속성 이름→값 조건 맵. 빈 맵이면 레이블 전체를 반환.
     * @return 조건에 맞는 [GraphVertex] Flow.
     */
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphVertex>

    /**
     * 기존 정점의 속성을 갱신하고 갱신된 정점을 반환한다.
     *
     * ```kotlin
     * val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 31))
     * ```
     *
     * @param label 정점 레이블.
     * @param id 갱신할 정점 ID.
     * @param properties 새 속성 맵 (기존 속성을 대체한다).
     * @return 갱신된 [GraphVertex], 해당 ID가 없으면 `null`.
     */
    suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?

    /**
     * 정점을 삭제한다.
     *
     * ```kotlin
     * val deleted = ops.deleteVertex("Person", vertex.id)  // true
     * ```
     *
     * @param label 정점 레이블.
     * @param id 삭제할 정점 ID.
     * @return 삭제 성공이면 `true`, 해당 ID가 없으면 `false`.
     */
    suspend fun deleteVertex(label: String, id: GraphElementId): Boolean

    /**
     * 레이블로 정점 수를 반환한다.
     *
     * ```kotlin
     * val count = ops.countVertices("Person")  // 1L
     * ```
     *
     * @param label 카운트할 정점 레이블.
     * @return 해당 레이블의 정점 총 수.
     */
    suspend fun countVertices(label: String): Long
}

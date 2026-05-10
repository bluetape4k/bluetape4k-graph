package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex

/**
 * 그래프 정점(Vertex) CRUD 저장소 (동기 방식).
 *
 * ```kotlin
 * val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
 * val found  = ops.findVertexById("Person", vertex.id) // non-null
 * val list   = ops.findVerticesByLabel("Person", mapOf("age" to 30))
 * val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 31))
 * val deleted = ops.deleteVertex("Person", vertex.id) // true
 * val count  = ops.countVertices("Person") // 0L (deleted)
 * ```
 */
interface GraphVertexRepository {
    /**
     * 새 정점을 생성하고 반환한다.
     *
     * ```kotlin
     * val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
     * println(vertex.id)    // "v-1"
     * println(vertex.label) // "Person"
     * ```
     *
     * @param label 정점 레이블.
     * @param properties 정점에 저장할 속성 맵. 기본값은 빈 맵.
     * @return 백엔드에서 생성된 [GraphVertex] (ID가 채워진 상태).
     */
    fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex

    /**
     * 같은 레이블의 정점을 여러 개 생성하고 입력 순서와 같은 순서로 반환한다.
     *
     * ## 동작/계약
     *
     * - 빈 입력은 백엔드를 호출하지 않고 `emptyList()`를 반환한다.
     * - 기본 구현은 [createVertex]를 순차 호출하는 호환성 fallback이다.
     * - 기본 구현은 중간 실패 시 앞서 생성된 정점이 남을 수 있다.
     * - 성능 및 all-or-fail 의미가 필요한 프로덕션 백엔드는 이 메서드를 override해야 한다.
     *
     * ```kotlin
     * val vertices = ops.createVertices(
     *     "Person",
     *     listOf(
     *         mapOf("name" to "Alice"),
     *         mapOf("name" to "Bob"),
     *     )
     * )
     * ```
     *
     * @param label 정점 레이블.
     * @param propertiesList 각 정점에 저장할 속성 맵 목록.
     * @return 백엔드에서 생성된 [GraphVertex] 목록.
     */
    fun createVertices(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): List<GraphVertex> {
        GraphBatchValidation.validateVertexBatch(label, propertiesList)
        if (propertiesList.isEmpty()) return emptyList()
        return propertiesList.map { properties -> createVertex(label, properties) }
    }

    /**
     * ID로 단일 정점을 조회한다.
     *
     * ```kotlin
     * val found = ops.findVertexById("Person", vertex.id)  // non-null
     * val none  = ops.findVertexById("Person", GraphElementId.of("unknown"))  // null
     * ```
     *
     * @param label 정점 레이블.
     * @param id 조회할 정점 ID.
     * @return 존재하면 [GraphVertex], 없으면 `null`.
     */
    fun findVertexById(label: String, id: GraphElementId): GraphVertex?

    /**
     * 레이블 없이 ID로 단일 정점을 조회한다.
     *
     * 백엔드가 ID로만 정점을 조회할 수 있는 경우에 사용한다.
     * Dijkstra/A* 알고리즘에서 레이블 없이 ID만 알고 있을 때 필요하다.
     *
     * ```kotlin
     * val found = ops.findVertexById(vertex.id)  // 레이블 불필요
     * ```
     *
     * @param id 조회할 정점 ID.
     * @return 존재하면 [GraphVertex], 없으면 `null`.
     */
    fun findVertexById(id: GraphElementId): GraphVertex?

    /**
     * 레이블과 속성 필터로 정점 목록을 조회한다.
     *
     * ```kotlin
     * val all   = ops.findVerticesByLabel("Person")
     * val aged  = ops.findVerticesByLabel("Person", mapOf("age" to 30))
     * ```
     *
     * @param label 조회할 정점 레이블.
     * @param filter 속성 이름→값 조건 맵. 빈 맵이면 레이블 전체를 반환.
     * @return 조건에 맞는 [GraphVertex] 목록.
     */
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphVertex>

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
    fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?

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
    fun deleteVertex(label: String, id: GraphElementId): Boolean

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
    fun countVertices(label: String): Long
}

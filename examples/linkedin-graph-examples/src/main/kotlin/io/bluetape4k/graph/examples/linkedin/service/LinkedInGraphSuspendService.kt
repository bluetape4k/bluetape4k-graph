package io.bluetape4k.graph.examples.linkedin.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.Flow

/**
 * LinkedIn 인맥 그래프 서비스 (코루틴 suspend 버전).
 * [GraphSuspendOperations]을 사용해 소셜 네트워크 기능 구현.
 *
 * ```kotlin
 * val ops = TinkerGraphSuspendOperations()
 * val service = LinkedInGraphSuspendService(ops)
 *
 * runBlocking {
 *     service.initialize()
 *     val alice = service.addPerson("Alice", title = "Engineer")
 *     val bob   = service.addPerson("Bob",   title = "Manager")
 *     service.connect(alice.id, bob.id, since = "2020-01-01")
 *
 *     val friends = service.getDirectConnections(alice.id).toList()  // [Bob]
 *     val path    = service.findConnectionPath(alice.id, bob.id)
 * }
 * ```
 */
class LinkedInGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "linkedin",
) {
    companion object : KLoggingChannel()

    /** 그래프 초기화 */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
        }
    }

    /**
     * 사람 정점을 추가한다.
     *
     * @param name 이름.
     * @param title 직함.
     * @param company 현 소속 회사명.
     * @param location 위치.
     * @return 생성된 [GraphVertex].
     */
    suspend fun addPerson(
        name: String,
        title: String = "",
        company: String = "",
        location: String = "",
    ): GraphVertex = ops.createVertex(
        "Person",
        mapOf("name" to name, "title" to title, "company" to company, "location" to location)
    )

    /** 회사 추가 */
    suspend fun addCompany(
        name: String,
        industry: String = "",
        location: String = "",
    ): GraphVertex = ops.createVertex(
        "Company",
        mapOf("name" to name, "industry" to industry, "location" to location)
    )

    /** 인맥 연결 (양방향: A KNOWS B, B KNOWS A) */
    suspend fun connect(
        personId1: GraphElementId,
        personId2: GraphElementId,
        since: String = "",
        strength: Int = 5,
    ) {
        ops.createEdge(personId1, personId2, "KNOWS", mapOf("since" to since, "strength" to strength))
        ops.createEdge(personId2, personId1, "KNOWS", mapOf("since" to since, "strength" to strength))
    }

    /** 재직 정보 추가 */
    suspend fun addWorkExperience(
        personId: GraphElementId,
        companyId: GraphElementId,
        role: String,
        isCurrent: Boolean = false,
    ) {
        ops.createEdge(personId, companyId, "WORKS_AT", mapOf("role" to role, "isCurrent" to isCurrent))
    }

    /** 팔로우 */
    suspend fun follow(followerId: GraphElementId, targetId: GraphElementId) {
        ops.createEdge(followerId, targetId, "FOLLOWS", emptyMap())
    }

    /** 1촌 인맥 목록 */
    fun getDirectConnections(personId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(personId, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 1))

    /**
     * N촌 이내 인맥 목록을 [Flow]로 반환한다.
     *
     * @param personId 기준 사람 ID.
     * @param degree 최대 촌수.
     */
    fun getConnectionsWithinDegree(personId: GraphElementId, degree: Int): Flow<GraphVertex> =
        ops.neighbors(personId, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = degree))

    /**
     * 두 사람 사이 최단 인맥 경로를 탐색한다.
     *
     * @param fromId 출발 사람 ID.
     * @param toId 도착 사람 ID.
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     */
    suspend fun findConnectionPath(fromId: GraphElementId, toId: GraphElementId) =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "KNOWS", maxDepth = 6))

    /** 모든 연결 경로 (최대 3단계) */
    fun findAllConnectionPaths(fromId: GraphElementId, toId: GraphElementId) =
        ops.allPaths(fromId, toId, PathOptions(edgeLabel = "KNOWS", maxDepth = 3))

    /** 특정 회사 재직자 검색 */
    fun findEmployees(companyId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(companyId, NeighborOptions(edgeLabel = "WORKS_AT", direction = Direction.INCOMING, maxDepth = 1))

    /** 사람 검색 (이름으로) */
    fun findPersonByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Person", mapOf("name" to name))
}

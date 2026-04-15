package io.bluetape4k.graph.examples.linkedin.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging

/**
 * LinkedIn 인맥 그래프 서비스.
 * [GraphOperations]을 사용해 소셜 네트워크 기능 구현.
 *
 * ```kotlin
 * val ops = TinkerGraphOperations()
 * val service = LinkedInGraphService(ops)
 * service.initialize()
 *
 * val alice = service.addPerson("Alice", title = "Engineer")
 * val bob   = service.addPerson("Bob",   title = "Manager")
 * service.connect(alice.id, bob.id, since = "2020-01-01")
 *
 * val friends = service.getDirectConnections(alice.id)  // [Bob]
 * val path    = service.findConnectionPath(alice.id, bob.id)
 * ```
 */
class LinkedInGraphService(
    private val ops: GraphOperations,
    private val graphName: String = "linkedin",
) {
    companion object : KLogging()

    /**
     * 그래프 초기화. 존재하지 않으면 생성한다.
     *
     * ```kotlin
     * service.initialize()
     * ```
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info("LinkedIn graph '{}' created", graphName)
        }
    }

    /**
     * 사람 정점을 추가한다.
     *
     * ```kotlin
     * val alice = service.addPerson("Alice", title = "Engineer", company = "Bluetape4k")
     * ```
     *
     * @param name 이름.
     * @param title 직함.
     * @param company 현 소속 회사명.
     * @param location 위치.
     * @return 생성된 [GraphVertex].
     */
    fun addPerson(
        name: String,
        title: String = "",
        company: String = "",
        location: String = "",
    ): GraphVertex = ops.createVertex(
        "Person",
        mapOf("name" to name, "title" to title, "company" to company, "location" to location)
    )

    /**
     * 회사 정점을 추가한다.
     *
     * ```kotlin
     * val company = service.addCompany("Bluetape4k", industry = "Software")
     * ```
     */
    fun addCompany(
        name: String,
        industry: String = "",
        location: String = "",
    ): GraphVertex = ops.createVertex(
        "Company",
        mapOf("name" to name, "industry" to industry, "location" to location)
    )

    /**
     * 인맥 연결을 추가한다 (양방향: A KNOWS B, B KNOWS A).
     *
     * ```kotlin
     * service.connect(alice.id, bob.id, since = "2022-01-01", strength = 8)
     * ```
     *
     * @param personId1 첫 번째 사람 ID.
     * @param personId2 두 번째 사람 ID.
     * @param since 연결 시작 날짜 (ISO 문자열).
     * @param strength 관계 강도 (1-10).
     */
    fun connect(
        personId1: GraphElementId,
        personId2: GraphElementId,
        since: String = "",
        strength: Int = 5,
    ) {
        ops.createEdge(personId1, personId2, "KNOWS", mapOf("since" to since, "strength" to strength))
        ops.createEdge(personId2, personId1, "KNOWS", mapOf("since" to since, "strength" to strength))
    }

    /**
     * 재직 정보 간선을 추가한다.
     *
     * ```kotlin
     * service.addWorkExperience(alice.id, company.id, role = "Engineer", isCurrent = true)
     * ```
     */
    fun addWorkExperience(
        personId: GraphElementId,
        companyId: GraphElementId,
        role: String,
        isCurrent: Boolean = false,
    ) {
        ops.createEdge(personId, companyId, "WORKS_AT", mapOf("role" to role, "isCurrent" to isCurrent))
    }

    /**
     * 팔로우 관계 간선을 추가한다.
     *
     * ```kotlin
     * service.follow(alice.id, bob.id)
     * ```
     */
    fun follow(followerId: GraphElementId, targetId: GraphElementId) {
        ops.createEdge(followerId, targetId, "FOLLOWS", emptyMap())
    }

    /**
     * 1촌 인맥 목록을 반환한다.
     *
     * ```kotlin
     * val friends = service.getDirectConnections(alice.id)
     * ```
     */
    fun getDirectConnections(personId: GraphElementId): List<GraphVertex> =
        ops.neighbors(personId, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 1))

    /**
     * N촌 이내 인맥 목록을 반환한다.
     *
     * ```kotlin
     * val secondDegree = service.getConnectionsWithinDegree(alice.id, degree = 2)
     * ```
     *
     * @param personId 기준 사람 ID.
     * @param degree 최대 촌수.
     * @return [GraphVertex] 목록.
     */
    fun getConnectionsWithinDegree(personId: GraphElementId, degree: Int): List<GraphVertex> =
        ops.neighbors(personId, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = degree))

    /**
     * 두 사람 사이 최단 인맥 경로를 탐색한다.
     *
     * ```kotlin
     * val path = service.findConnectionPath(alice.id, carol.id)
     * ```
     *
     * @param fromId 출발 사람 ID.
     * @param toId 도착 사람 ID.
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     */
    fun findConnectionPath(fromId: GraphElementId, toId: GraphElementId) =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "KNOWS", maxDepth = 6))

    /**
     * 두 사람 사이 모든 연결 경로를 탐색한다 (최대 3단계).
     *
     * ```kotlin
     * val paths = service.findAllConnectionPaths(alice.id, carol.id)
     * ```
     *
     * @param fromId 출발 사람 ID.
     * @param toId 도착 사람 ID.
     * @return [GraphPath] 목록.
     */
    fun findAllConnectionPaths(fromId: GraphElementId, toId: GraphElementId) =
        ops.allPaths(fromId, toId, PathOptions(edgeLabel = "KNOWS", maxDepth = 3))

    /**
     * 특정 회사 재직자를 검색한다.
     *
     * ```kotlin
     * val employees = service.findEmployees(bluetape4k.id)
     * ```
     *
     * @param companyId 회사 정점 ID.
     * @return 재직자 [GraphVertex] 목록.
     */
    fun findEmployees(companyId: GraphElementId): List<GraphVertex> =
        ops.neighbors(companyId, NeighborOptions(edgeLabel = "WORKS_AT", direction = Direction.INCOMING, maxDepth = 1))

    /**
     * 사람 이름으로 검색한다.
     *
     * ```kotlin
     * val persons = service.findPersonByName("Alice")
     * ```
     */
    fun findPersonByName(name: String): List<GraphVertex> =
        ops.findVerticesByLabel("Person", mapOf("name" to name))
}

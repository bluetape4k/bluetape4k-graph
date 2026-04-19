package io.bluetape4k.graph.neo4j.benchmark

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

/**
 * Neo4j 벤치마크 공유 상태.
 *
 * - Testcontainers 싱글턴 [Neo4jServer] 로 Neo4j 기동
 * - Neo4j Java Driver 로 Bolt 연결
 * - 정점 4개 / 간선 4개로 구성된 소규모 그래프(체인 + 지름길)를 매 시행마다 사용
 */
@State(Scope.Benchmark)
open class Neo4jBenchmarkState {

    companion object: KLogging() {
        const val GRAPH_NAME = "neo4j"
        const val PERSON_LABEL = "Person"
        const val KNOWS_LABEL = "KNOWS"
        const val FOLLOWS_LABEL = "FOLLOWS"
    }

    private lateinit var driver: Driver

    lateinit var ops: Neo4jGraphOperations

    var aliceId: GraphElementId = GraphElementId("0")
    var bobId: GraphElementId = GraphElementId("0")
    var charlieId: GraphElementId = GraphElementId("0")
    var daveId: GraphElementId = GraphElementId("0")

    @Setup
    fun setup() {
        val server = Neo4jServer.Launcher.neo4j
        driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
        ops = Neo4jGraphOperations(driver)

        // 기존 데이터 초기화 (Neo4j 는 그래프 단위가 아닌 데이터베이스 전체 삭제)
        ops.dropGraph(GRAPH_NAME)

        val alice = ops.createVertex(PERSON_LABEL, mapOf("name" to "Alice", "age" to 30L))
        val bob = ops.createVertex(PERSON_LABEL, mapOf("name" to "Bob", "age" to 25L))
        val charlie = ops.createVertex(PERSON_LABEL, mapOf("name" to "Charlie", "age" to 28L))
        val dave = ops.createVertex(PERSON_LABEL, mapOf("name" to "Dave", "age" to 35L))

        aliceId = alice.id
        bobId = bob.id
        charlieId = charlie.id
        daveId = dave.id

        ops.createEdge(aliceId, bobId, KNOWS_LABEL, mapOf("since" to 2020L))
        ops.createEdge(bobId, charlieId, KNOWS_LABEL, mapOf("since" to 2021L))
        ops.createEdge(charlieId, daveId, KNOWS_LABEL, mapOf("since" to 2022L))
        ops.createEdge(aliceId, charlieId, FOLLOWS_LABEL, mapOf("since" to 2019L))
    }

    @TearDown
    fun teardown() {
        runCatching { ops.dropGraph(GRAPH_NAME) }
        driver.close()
    }
}

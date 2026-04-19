package io.bluetape4k.graph.age.benchmark

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

/**
 * Apache AGE 벤치마크 공유 상태.
 *
 * - Testcontainers 싱글턴 [PostgreSQLAgeServer] 로 PostgreSQL + AGE 기동
 * - HikariCP + Exposed `Database.connect` 로 DataSource 바인딩
 * - 정점 4개 / 간선 4개로 구성된 소규모 그래프(체인 + 지름길)를 매 시행마다 사용
 */
@State(Scope.Benchmark)
open class AgeBenchmarkState {

    companion object: KLogging() {
        const val GRAPH_NAME = "bench_graph"
        const val PERSON_LABEL = "Person"
        const val KNOWS_LABEL = "KNOWS"
        const val FOLLOWS_LABEL = "FOLLOWS"
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    lateinit var ops: GraphOperations

    var aliceId: GraphElementId = GraphElementId("0")
    var bobId: GraphElementId = GraphElementId("0")
    var charlieId: GraphElementId = GraphElementId("0")
    var daveId: GraphElementId = GraphElementId("0")

    @Setup
    fun setup() {
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 4
        })
        database = Database.connect(dataSource)
        val raw = AgeGraphOperations(GRAPH_NAME)
        ops = BenchmarkSingleThreadedCachingAgeGraphOperations(raw)

        if (ops.graphExists(GRAPH_NAME)) {
            ops.dropGraph(GRAPH_NAME)
        }
        ops.createGraph(GRAPH_NAME)

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
        dataSource.close()
    }
}

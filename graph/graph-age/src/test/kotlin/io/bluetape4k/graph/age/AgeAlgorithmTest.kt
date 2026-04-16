package io.bluetape4k.graph.age

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeAlgorithmTest {

    companion object: KLogging()

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var ops: AgeGraphOperations

    private val graphName = "algo_test_graph"

    @BeforeAll
    fun setup() {
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        database = Database.connect(dataSource)
        ops = AgeGraphOperations(graphName)
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
    }

    @BeforeEach
    fun resetGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        ops.createGraph(graphName)
    }

    @Test
    fun `degreeCentrality counts both directions`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
    }

    @Test
    fun `pageRank returns descending scores`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(4) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
        scores.shouldNotBeEmpty()
        scores.zipWithNext { x, y -> (x.score >= y.score).shouldBeTrue() }
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `connectedComponents groups linked vertices`() {
        val a1 = ops.createVertex("Person", mapOf("g" to "A"))
        val a2 = ops.createVertex("Person", mapOf("g" to "A"))
        val b1 = ops.createVertex("Person", mapOf("g" to "B"))
        val b2 = ops.createVertex("Person", mapOf("g" to "B"))
        ops.createEdge(a1.id, a2.id, "REL")
        ops.createEdge(b1.id, b2.id, "REL")

        val components = ops.connectedComponents(ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"))
        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `bfs returns visits up to maxDepth`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2))
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 3
    }

    @Test
    fun `dfs returns visits starting from given vertex`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2))
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles finds triangle`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")

        val cycles = ops.detectCycles(CycleOptions(edgeLabel = "E", maxDepth = 5))
        cycles.shouldNotBeEmpty()
    }
}

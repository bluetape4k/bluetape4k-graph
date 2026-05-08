package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.assertions.shouldBeNear
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import kotlin.math.sqrt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphWeightedPathTest {

    companion object : KLogging()

    private lateinit var driver: Driver
    private lateinit var ops: MemgraphGraphOperations

    @BeforeAll
    fun setup() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        ops = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun clearGraph() {
        ops.dropGraph("default")
    }

    @Test
    fun `가중치 최단 경로 A에서 C까지는 A-B-C이다`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))
        val c = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "ROAD", mapOf("cost" to 1.0))
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))
        ops.createEdge(a.id, c.id, "ROAD", mapOf("cost" to 5.0))

        val path = ops.shortestPath(
            a.id, c.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
    }

    @Test
    fun `가중치 없는 간선 Skip 정책에서 경로 없음`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))
        val c = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "ROAD")
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))

        ops.shortestPath(
            a.id, c.id,
            PathOptions(
                weightProperty = "cost",
                edgeLabel = "ROAD",
                missingWeightPolicy = MissingWeightPolicy.Skip,
            ),
        ).shouldBeNull()
    }

    @Test
    fun `연결되지 않으면 가중치 경로는 null`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))

        ops.shortestPath(a.id, b.id, PathOptions(weightProperty = "cost")).shouldBeNull()
    }

    @Test
    fun `AStar 경로 A에서 C까지는 A-B-C이다`() {
        val a = ops.createVertex("City", mapOf("name" to "A", "x" to 0.0, "y" to 0.0))
        val b = ops.createVertex("City", mapOf("name" to "B", "x" to 1.0, "y" to 0.0))
        val c = ops.createVertex("City", mapOf("name" to "C", "x" to 2.0, "y" to 0.0))
        ops.createEdge(a.id, b.id, "ROAD", mapOf("cost" to 1.0))
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))
        ops.createEdge(a.id, c.id, "ROAD", mapOf("cost" to 5.0))

        val goalX = c.properties["x"] as? Double ?: 2.0
        val goalY = c.properties["y"] as? Double ?: 0.0

        val path = ops.aStarPath(
            a.id, c.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ) { v ->
            val vx = v.properties["x"] as? Double ?: 0.0
            val vy = v.properties["y"] as? Double ?: 0.0
            sqrt((vx - goalX) * (vx - goalX) + (vy - goalY) * (vy - goalY))
        }.shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
    }
}

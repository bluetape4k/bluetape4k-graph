package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.amshove.kluent.shouldBeNear
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import kotlin.math.sqrt

/**
 * Neo4j 가중치 최단 경로(Dijkstra/A*) 통합 테스트.
 *
 * 테스트 그래프 (ROAD 레이블):
 *
 * A --(1.0)--> B --(2.0)--> C
 * |                          ^
 * +--------(5.0)-------------+
 *
 * 최단 경로 A→C: A→B→C (cost=3.0)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jWeightedPathTest {

    companion object : KLogging()

    private lateinit var driver: Driver
    private lateinit var ops: Neo4jGraphOperations

    @BeforeAll
    fun setup() {
        val server = Neo4jServer.Launcher.neo4j
        driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
        ops = Neo4jGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun clearGraph() {
        ops.dropGraph("default")
    }

    // ─── Dijkstra ───────────────────────────────────────────────────────────────

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
        path.vertices.size shouldBeInRange 2..3
    }

    @Test
    fun `가중치 없는 간선 Skip 정책에서 경로 없음`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))
        val c = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "ROAD")              // cost 없음
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))

        val result = ops.shortestPath(
            a.id, c.id,
            PathOptions(
                weightProperty = "cost",
                edgeLabel = "ROAD",
                missingWeightPolicy = MissingWeightPolicy.Skip,
            ),
        )

        result.shouldBeNull()
    }

    @Test
    fun `가중치 없는 간선 UseDefault 정책에서 기본값 적용`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))
        val c = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "ROAD")              // cost 없음 → 기본값 1.0
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))

        val path = ops.shortestPath(
            a.id, c.id,
            PathOptions(
                weightProperty = "cost",
                edgeLabel = "ROAD",
                missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
            ),
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
    }

    @Test
    fun `연결되지 않으면 가중치 경로는 null`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))

        val result = ops.shortestPath(
            a.id, b.id,
            PathOptions(weightProperty = "cost"),
        )

        result.shouldBeNull()
    }

    // ─── A* ─────────────────────────────────────────────────────────────────────

    @Suppress("DANGEROUS_CHARACTERS")
    @Test
    fun `A* 경로 A에서 C까지는 A-B-C이다`() {
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

    @Suppress("DANGEROUS_CHARACTERS")
    @Test
    fun `A* 연결 없으면 null 반환`() {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))

        val result = ops.aStarPath(
            a.id, b.id,
            PathOptions(weightProperty = "cost"),
        ) { _ -> 0.0 }

        result.shouldBeNull()
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private infix fun Int.shouldBeInRange(range: IntRange) {
        assert(this in range) { "Expected $this to be in $range" }
    }
}

package io.bluetape4k.graph.memgraph

import io.bluetape4k.assertions.shouldBeNear
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphSuspendWeightedPathTest {

    companion object : KLogging()

    private lateinit var driver: Driver
    private lateinit var ops: MemgraphGraphSuspendOperations

    @BeforeAll
    fun setup() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        ops = MemgraphGraphSuspendOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun clearGraph() = runSuspendIO {
        ops.dropGraph("default")
    }

    @Test
    fun `Dijkstra 최단 경로 A에서 C까지는 A-B-C이다`() = runSuspendIO {
        val (a, _, c) = createWeightedRoadGraph()

        val path = ops.shortestPath(
            a.id,
            c.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
    }

    @Test
    fun `Skip 정책에서 weight 없는 간선은 경로에서 제외한다`() = runSuspendIO {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))
        val c = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "ROAD")
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))

        ops.shortestPath(
            a.id,
            c.id,
            PathOptions(
                weightProperty = "cost",
                edgeLabel = "ROAD",
                missingWeightPolicy = MissingWeightPolicy.Skip,
            ),
        ).shouldBeNull()
    }

    @Test
    fun `연결되지 않으면 가중치 경로는 null이다`() = runSuspendIO {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val isolated = ops.createVertex("City", mapOf("name" to "Z"))

        ops.shortestPath(
            a.id,
            isolated.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ).shouldBeNull()
    }

    @Test
    fun `AStar 최단 경로 A에서 C까지는 A-B-C이다`() = runSuspendIO {
        val (a, _, c) = createWeightedRoadGraph()

        val path = ops.aStarPath(
            a.id,
            c.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ) { _ -> 0.0 }.shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
    }

    private suspend fun createWeightedRoadGraph(): WeightedRoadGraph {
        val a = ops.createVertex("City", mapOf("name" to "A"))
        val b = ops.createVertex("City", mapOf("name" to "B"))
        val c = ops.createVertex("City", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "ROAD", mapOf("cost" to 1.0))
        ops.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))
        ops.createEdge(a.id, c.id, "ROAD", mapOf("cost" to 5.0))
        return WeightedRoadGraph(a, b, c)
    }

    private data class WeightedRoadGraph(val a: GraphVertex, val b: GraphVertex, val c: GraphVertex)
}

package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.servers.MemgraphServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphAlgorithmTest {

    companion object: KLogging()

    private val driver = GraphDatabase.driver(MemgraphServer.boltUrl, AuthTokens.none())
    private val ops = MemgraphGraphOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun reset() {
        ops.dropGraph("default")
    }

    @Test
    fun `sanitizeLabel throws on injection string`() {
        val ex = assertThrows<IllegalArgumentException> {
            ops.degreeCentrality(
                GraphElementId.of("dummy"),
                DegreeOptions(edgeLabel = "Person'; DROP TABLE foo--"),
            )
        }
        ex.message?.shouldContain("Invalid label")
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
    fun `detectCycles finds triangle via Cypher`() {
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

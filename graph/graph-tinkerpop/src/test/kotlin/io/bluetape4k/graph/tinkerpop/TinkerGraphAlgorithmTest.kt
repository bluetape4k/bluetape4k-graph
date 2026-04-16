package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeIn
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphAlgorithmTest {

    companion object: KLogging()

    private val ops = TinkerGraphOperations()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun reset() {
        ops.dropGraph("default")
    }

    @Test
    fun `degreeCentrality counts in and out edges`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
        degree.total shouldBeEqualTo 2
    }

    @Test
    fun `pageRank returns score-descending list`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(4) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
        scores.shouldNotBeEmpty()
        // descending order
        scores.zipWithNext { a, b -> (a.score >= b.score).shouldBeTrue() }
        // hub should be top
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `connectedComponents finds two clusters`() {
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
    fun `bfs returns level-ordered visits`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        val d = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(a.id, c.id, "E")
        ops.createEdge(b.id, d.id, "E")
        ops.createEdge(c.id, d.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3))
        visits.first().depth shouldBeEqualTo 0
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 4
    }

    @Test
    fun `dfs starts from given vertex`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3))
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

        val cycles = ops.detectCycles(CycleOptions(maxDepth = 5))
        cycles.shouldNotBeEmpty()
        cycles.first().path.vertices.first().id shouldBeEqualTo cycles.first().path.vertices.last().id
    }
}

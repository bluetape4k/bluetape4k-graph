package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphAlgorithmSuspendTest {

    companion object: KLoggingChannel()

    private val ops = TinkerGraphSuspendOperations()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun reset() = runSuspendIO {
        ops.dropGraph("default")
    }

    @Test
    fun `pageRank Flow emits descending scores`() = runSuspendIO {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = ops.pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30)).toList()
        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfs Flow emits visits in level order`() = runSuspendIO {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.bfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)).toList()
        visits.first().depth shouldBeEqualTo 0
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `degreeCentrality delegates through suspend adapter`() = runSuspendIO {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))

        degree.inDegree shouldBeEqualTo 1
        degree.outDegree shouldBeEqualTo 1
        degree.total shouldBeEqualTo 2
    }

    @Test
    fun `connectedComponents Flow emits component groups`() = runSuspendIO {
        val a1 = ops.createVertex("Person", mapOf("group" to "A"))
        val a2 = ops.createVertex("Person", mapOf("group" to "A"))
        val b1 = ops.createVertex("Person", mapOf("group" to "B"))
        val b2 = ops.createVertex("Person", mapOf("group" to "B"))
        ops.createEdge(a1.id, a2.id, "REL")
        ops.createEdge(b1.id, b2.id, "REL")

        val components = ops.connectedComponents(ComponentOptions(vertexLabel = "Person", edgeLabel = "REL")).toList()

        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `dfs Flow starts from the requested vertex`() = runSuspendIO {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 3)).toList()

        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles Flow emits triangle cycles`() = runSuspendIO {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")

        val cycles = ops.detectCycles(CycleOptions(maxDepth = 5)).toList()

        cycles.shouldNotBeEmpty()
        cycles.first().path.vertices.first().id shouldBeEqualTo cycles.first().path.vertices.last().id
    }
}

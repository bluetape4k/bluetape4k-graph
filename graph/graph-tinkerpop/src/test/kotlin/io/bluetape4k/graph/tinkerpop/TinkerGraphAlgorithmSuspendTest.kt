package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphAlgorithmSuspendTest: AbstractTinkerGraphTest() {

    companion object: KLoggingChannel()

    @Test
    fun `pageRank Flow emits descending scores`() = runSuspendIO {
        val hub = suspendOps.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = suspendOps.createVertex("Person", mapOf("name" to "L$i"))
            suspendOps.createEdge(leaf.id, hub.id, "FOLLOWS")
        }

        val scores = suspendOps
            .pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
            .log("Rank")
            .toList()

        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfs Flow emits visits in level order`() = runSuspendIO {
        val a = suspendOps.createVertex("Node", emptyMap())
        val b = suspendOps.createVertex("Node", emptyMap())
        suspendOps.createEdge(a.id, b.id, "E")

        val visits = suspendOps
            .bfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .log("bfs")
            .toList()

        visits.first().depth shouldBeEqualTo 0
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `degreeCentrality delegates through suspend adapter`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))
        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(c.id, a.id, "KNOWS")

        val degree = suspendOps.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))

        log.debug { "degree=$degree" }
        degree.inDegree shouldBeEqualTo 1
        degree.outDegree shouldBeEqualTo 1
        degree.total shouldBeEqualTo 2
    }

    @Test
    fun `connectedComponents Flow emits component groups`() = runSuspendIO {
        val a1 = suspendOps.createVertex("Person", mapOf("group" to "A"))
        val a2 = suspendOps.createVertex("Person", mapOf("group" to "A"))
        val b1 = suspendOps.createVertex("Person", mapOf("group" to "B"))
        val b2 = suspendOps.createVertex("Person", mapOf("group" to "B"))
        suspendOps.createEdge(a1.id, a2.id, "REL")
        suspendOps.createEdge(b1.id, b2.id, "REL")

        val components = suspendOps
            .connectedComponents(ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"))
            .log("connectedComponents")
            .toList()

        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `dfs Flow starts from the requested vertex`() = runSuspendIO {
        val a = suspendOps.createVertex("Node", emptyMap())
        val b = suspendOps.createVertex("Node", emptyMap())
        suspendOps.createEdge(a.id, b.id, "E")

        val visits = suspendOps
            .dfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 3))
            .log("dfs")
            .toList()

        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles Flow emits triangle cycles`() = runSuspendIO {
        val a = suspendOps.createVertex("Node", emptyMap())
        val b = suspendOps.createVertex("Node", emptyMap())
        val c = suspendOps.createVertex("Node", emptyMap())
        suspendOps.createEdge(a.id, b.id, "E")
        suspendOps.createEdge(b.id, c.id, "E")
        suspendOps.createEdge(c.id, a.id, "E")

        val cycles = suspendOps
            .detectCycles(CycleOptions(maxDepth = 5))
            .log("Cycle")
            .toList()

        cycles.shouldNotBeEmpty()
        cycles.first().path.vertices.first().id shouldBeEqualTo cycles.first().path.vertices.last().id
    }
}

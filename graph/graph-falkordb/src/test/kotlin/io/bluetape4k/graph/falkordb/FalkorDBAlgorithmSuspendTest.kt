package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeTrue
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
class FalkorDBAlgorithmSuspendTest: AbstractFalkorDBTest() {

    companion object: KLoggingChannel()

    @Test
    fun `degreeCentrality counts both directions`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))
        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(c.id, a.id, "KNOWS")

        val degree = suspendOps.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))

        log.debug { "degree=$degree" }
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
    }

    @Test
    fun `pageRank Flow returns descending scores`() = runSuspendIO {
        val hub = suspendOps.createVertex("Person", mapOf("name" to "Hub"))
        repeat(4) { i ->
            val leaf = suspendOps.createVertex("Person", mapOf("name" to "L$i"))
            suspendOps.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = suspendOps
            .pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
            .log("Score")
            .toList()

        scores.shouldNotBeEmpty()
        scores.zipWithNext { x, y -> (x.score >= y.score).shouldBeTrue() }
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `connectedComponents Flow groups linked vertices`() = runSuspendIO {
        val a1 = suspendOps.createVertex("Person", mapOf("g" to "A"))
        val a2 = suspendOps.createVertex("Person", mapOf("g" to "A"))
        val b1 = suspendOps.createVertex("Person", mapOf("g" to "B"))
        val b2 = suspendOps.createVertex("Person", mapOf("g" to "B"))

        suspendOps.createEdge(a1.id, a2.id, "REL")
        suspendOps.createEdge(b1.id, b2.id, "REL")

        val components = suspendOps
            .connectedComponents(
                ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"),
            )
            .log("Components")
            .toList()
        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `bfs Flow returns visits up to maxDepth`() = runSuspendIO {
        val a = suspendOps.createVertex("Node", emptyMap())
        val b = suspendOps.createVertex("Node", emptyMap())
        val c = suspendOps.createVertex("Node", emptyMap())

        suspendOps.createEdge(a.id, b.id, "E")
        suspendOps.createEdge(b.id, c.id, "E")

        val visits = suspendOps
            .bfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .log("BFS")
            .toList()
        
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 3
    }

    @Test
    fun `dfs Flow returns visits starting from given vertex`() = runSuspendIO {
        val a = suspendOps.createVertex("Node", emptyMap())
        val b = suspendOps.createVertex("Node", emptyMap())
        suspendOps.createEdge(a.id, b.id, "E")

        val visits = suspendOps
            .dfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .log("DFS")
            .toList()

        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles Flow finds triangle`() = runSuspendIO {
        val a = suspendOps.createVertex("Node", emptyMap())
        val b = suspendOps.createVertex("Node", emptyMap())
        val c = suspendOps.createVertex("Node", emptyMap())

        suspendOps.createEdge(a.id, b.id, "E")
        suspendOps.createEdge(b.id, c.id, "E")
        suspendOps.createEdge(c.id, a.id, "E")

        val cycles = suspendOps
            .detectCycles(CycleOptions(edgeLabel = "E", maxDepth = 5))
            .log("Cycles")
            .toList()
        cycles.shouldNotBeEmpty()
    }
}

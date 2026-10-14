package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Neo4jAlgorithmSuspendTest: AbstractNeo4jTest() {

    companion object: KLoggingChannel()

    @BeforeEach
    override fun beforeEach() = runSuspendIO {
        suspendOps.dropGraph(graphName)
    }

    @Test
    fun `pageRank Flow emits descending scores`() = runSuspendIO {
        val hub = suspendOps.createVertex("Person", "name" to "Hub")
        repeat(3) { i ->
            val leaf = suspendOps.createVertex("Person", "name" to "L$i")
            suspendOps.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = suspendOps
            .pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
            .log("PageRank")
            .toList()

        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfs Flow emits visits`() = runSuspendIO {
        val a = suspendOps.createVertex("Node")
        val b = suspendOps.createVertex("Node")
        suspendOps.createEdge(a.id, b.id, "E")

        val visits = suspendOps
            .bfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .log("Bfs")
            .toList()

        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `dfs Flow emits visits`() = runSuspendIO {
        val a = suspendOps.createVertex("Node")
        val b = suspendOps.createVertex("Node")
        suspendOps.createEdge(a.id, b.id, "E")

        val visits = suspendOps
            .dfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .log("dfs")
            .toList()

        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles finds triangle via Cypher`() = runSuspendIO {
        val a = suspendOps.createVertex("Node")
        val b = suspendOps.createVertex("Node")
        val c = suspendOps.createVertex("Node")
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

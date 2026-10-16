package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.algo.provider.GraphAlgorithmExecutionPath
import io.bluetape4k.graph.algo.provider.GraphAlgorithmFallbackReason
import io.bluetape4k.graph.algo.provider.GraphAlgorithmId
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class Neo4jAlgorithmTest: AbstractNeo4jTest() {

    companion object: KLogging()

    @Test
    fun `sanitizeLabel throws on injection string`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            ops.degreeCentrality(
                "dummy".toGraphElementId(),
                DegreeOptions(edgeLabel = "Person'; DROP TABLE foo--"),
            )
        }
        ex.message?.shouldContain("valid identifier")
    }

    @Test
    fun `degreeCentrality counts both directions`() {
        val a = ops.createVertex("Person", "name" to "A")
        val b = ops.createVertex("Person", "name" to "B")
        val c = ops.createVertex("Person", "name" to "C")

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))
        log.debug { "degree: $degree" }
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
    }

    @Test
    fun `pageRank returns descending scores`() {
        val hub = ops.createVertex("Person", "name" to "Hub")
        repeat(4) { i ->
            val leaf = ops.createVertex("Person", "name" to "L$i")
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }

        val scores = ops
            .pageRank(PageRankOptions(vertexLabel = "Person", iterations = 30))
            .onEach { log.debug { "rank source=$it" } }

        scores.shouldNotBeEmpty()
        // descending order
        scores.zipWithNext { x, y -> x.score shouldBeGreaterOrEqualTo y.score }
        // hub should be top
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `pageRank exposes the JVM fallback execution`() {
        ops.pageRank(PageRankOptions(vertexLabel = "Person", topK = 1))

        val execution = ops.lastAlgorithmExecution.shouldNotBeNull()

        log.debug { "execution=$execution" }
        execution.algorithm shouldBeEqualTo GraphAlgorithmId.PAGE_RANK
        execution.path shouldBeEqualTo GraphAlgorithmExecutionPath.JVM_FALLBACK
        execution.fallbackReason shouldBeEqualTo GraphAlgorithmFallbackReason.NO_PROVIDER
    }

    @Test
    fun `connectedComponents groups linked vertices`() {
        val a1 = ops.createVertex("Person", "g" to "A")
        val a2 = ops.createVertex("Person", "g" to "A")
        val b1 = ops.createVertex("Person", "g" to "B")
        val b2 = ops.createVertex("Person", "g" to "B")

        ops.createEdge(a1.id, a2.id, "REL")
        ops.createEdge(b1.id, b2.id, "REL")

        val components = ops
            .connectedComponents(ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"))
            .onEach { log.debug { "connectedComponents=$it" } }

        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `bfs returns visits up to maxDepth`() {
        val a = ops.createVertex("Node")
        val b = ops.createVertex("Node")
        val c = ops.createVertex("Node")
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")

        val visits = ops
            .bfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .onEach { log.debug { "visits=$it" } }

        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 3
    }

    @Test
    fun `dfs returns visits starting from given vertex`() {
        val a = ops.createVertex("Node")
        val b = ops.createVertex("Node")
        ops.createEdge(a.id, b.id, "E")

        val visits = ops
            .dfs(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
            .onEach { log.debug { "visits=$it" } }

        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `detectCycles finds triangle via Cypher`() {
        val a = ops.createVertex("Node")
        val b = ops.createVertex("Node")
        val c = ops.createVertex("Node")
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")

        val cycles = ops
            .detectCycles(CycleOptions(edgeLabel = "E", maxDepth = 5))
            .onEach { log.debug { "cycle=$it" } }

        cycles.shouldNotBeEmpty()
    }
}

package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadAlgorithmAdapterTest {

    companion object: KLogging()

    private val ops = TinkerGraphOperations()
    private val vtOps = ops.asVirtualThread()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun reset() {
        ops.dropGraph("default")
    }

    @Test
    fun `pageRankAsync returns CompletableFuture with descending scores`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = vtOps.pageRankAsync(PageRankOptions(vertexLabel = "Person", iterations = 30)).join()
        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfsAsync returns visits`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        val visits = vtOps.bfsAsync(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)).join()
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `concurrent virtual thread executions complete`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val futures = (1..10).map { vtOps.bfsAsync(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)) }
        val results = futures.map { it.join() }
        results.size shouldBeEqualTo 10
        results.all { it.isNotEmpty() } shouldBeEqualTo true
    }

    @Test
    fun `GraphOperations as virtual thread returns adapter`() {
        val opsAsAlgo: io.bluetape4k.graph.repository.GraphAlgorithmRepository = ops
        val vt = opsAsAlgo.asVirtualThread()
        val future = vt.degreeCentralityAsync(io.bluetape4k.graph.model.GraphElementId.of("0"))
        future.join() // should not throw even when vertex absent — returns 0/0
    }
}

package io.bluetape4k.graph.algo

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.concurrent.get
import io.bluetape4k.concurrent.sequence
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.graph.model.elementIdOf
import io.bluetape4k.graph.repository.GraphAlgorithmRepository
import io.bluetape4k.graph.tinkerpop.AbstractTinkerGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class VirtualThreadAlgorithmAdapterTest: AbstractTinkerGraphTest() {

    companion object: KLogging()

    @Test
    fun `pageRankAsync returns CompletableFuture with descending scores`() {
        val hub = ops.createVertex("Person", mapOf("name" to "Hub"))
        repeat(3) { i ->
            val leaf = ops.createVertex("Person", mapOf("name" to "L$i"))
            ops.createEdge(leaf.id, hub.id, "FOLLOWS")
        }
        val scores = vtOps.pageRankAsync(PageRankOptions(vertexLabel = "Person", iterations = 30)).get(1.seconds)

        scores.forEach { log.debug { "score=$it" } }
        scores.shouldNotBeEmpty()
        scores.first().vertex.properties["name"] shouldBeEqualTo "Hub"
    }

    @Test
    fun `bfsAsync returns visits`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = vtOps.bfsAsync(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2)).get(1.seconds)

        visits.forEach { log.debug { "visit=$it" } }
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    fun `concurrent virtual thread executions complete`() {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val futures = List(10) {
            vtOps.bfsAsync(a.id, TreeTraversalOptions(edgeLabel = "E", maxDepth = 2))
        }.sequence()
        val results = futures.get(1.seconds)

        results.forEach { log.debug { "result=$it" } }
        results.size shouldBeEqualTo 10
        results.all { it.isNotEmpty() }.shouldBeTrue()
    }

    @Test
    fun `GraphOperations as virtual thread returns adapter`() {
        val opsAsAlgo = ops as GraphAlgorithmRepository
        val vt = opsAsAlgo.asVirtualThread()

        val future = vt.degreeCentralityAsync(elementIdOf("0"))
        future.join() // should not throw even when vertex absent — returns 0/0
    }
}

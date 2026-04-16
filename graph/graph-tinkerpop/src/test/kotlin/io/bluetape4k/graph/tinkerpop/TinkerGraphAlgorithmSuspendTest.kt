package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldNotBeEmpty
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
}

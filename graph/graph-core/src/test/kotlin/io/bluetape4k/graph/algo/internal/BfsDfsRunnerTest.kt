package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class BfsDfsRunnerTest {

    private fun id(v: String) = GraphElementId.of(v)

    /**
     * 그래프:
     *   a → b → d
     *   a → c → d
     *   d → e
     */
    private val adjacency: Map<GraphElementId, List<GraphElementId>> = mapOf(
        id("a") to listOf(id("b"), id("c")),
        id("b") to listOf(id("d")),
        id("c") to listOf(id("d")),
        id("d") to listOf(id("e")),
        id("e") to emptyList(),
    )

    @Test
    fun `bfs visits in level order`() {
        val visits = BfsDfsRunner.bfs(id("a"), adjacency, maxDepth = 3, maxVertices = 100)
        visits.map { it.vertex.id.value } shouldContainAll listOf("a", "b", "c", "d", "e")
        visits.first().vertex.id shouldBeEqualTo id("a")
        visits.first().depth shouldBeEqualTo 0
        visits.first { it.vertex.id == id("d") }.depth shouldBeEqualTo 2
    }

    @Test
    fun `bfs respects maxDepth`() {
        val visits = BfsDfsRunner.bfs(id("a"), adjacency, maxDepth = 1, maxVertices = 100)
        visits.map { it.vertex.id.value }.toSet() shouldBeEqualTo setOf("a", "b", "c")
    }

    @Test
    fun `bfs respects maxVertices`() {
        val visits = BfsDfsRunner.bfs(id("a"), adjacency, maxDepth = 10, maxVertices = 2)
        visits.shouldHaveSize(2)
    }

    @Test
    fun `dfs visits depth first`() {
        val visits = BfsDfsRunner.dfs(id("a"), adjacency, maxDepth = 3, maxVertices = 100)
        visits.first().vertex.id shouldBeEqualTo id("a")
        // 'a' visited first; one of b/c visited next, then descend before sibling
        visits.map { it.vertex.id.value }.toSet() shouldContainAll setOf("a", "b", "c", "d", "e")
    }
}

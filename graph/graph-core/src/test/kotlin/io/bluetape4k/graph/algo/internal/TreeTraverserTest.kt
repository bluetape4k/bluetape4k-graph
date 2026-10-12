package io.bluetape4k.graph.algo.internal

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContainAll
import io.bluetape4k.assertions.shouldContainSame
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class TreeTraverserTest: AbstractGraphTest() {

    companion object: KLogging()

    /**
     * 그래프:
     * 
     * ```
     *   a → b → d → e
     *   a → c → d
     * ```
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
        val visits = TreeTraverser.bfs(id("a"), adjacency, maxDepth = 3, maxVertices = 100)

        visits.forEach { log.debug { "visit=$it" } }
        visits.map { it.vertex.id.value } shouldContainAll listOf("a", "b", "c", "d", "e")
        visits.first().vertex.id shouldBeEqualTo id("a")
        visits.first().depth shouldBeEqualTo 0
        visits.first { it.vertex.id == id("d") }.depth shouldBeEqualTo 2
    }

    @Test
    fun `bfs respects maxDepth`() {
        val visits = TreeTraverser.bfs(id("a"), adjacency, maxDepth = 1, maxVertices = 100)

        visits.forEach { log.debug { "visit=$it" } }
        visits.map { it.vertex.id.value }.toSet() shouldBeEqualTo setOf("a", "b", "c")
    }

    @Test
    fun `bfs maxDepth 0 returns only the start vertex`() {
        val visits = TreeTraverser.bfs(id("a"), adjacency, maxDepth = 0, maxVertices = 100)

        visits shouldHaveSize 1
        visits.single().vertex.id shouldBeEqualTo id("a")
    }

    @Test
    fun `dfs maxDepth 0 returns only the start vertex`() {
        val visits = TreeTraverser.dfs(id("a"), adjacency, maxDepth = 0, maxVertices = 100)

        visits shouldHaveSize 1
        visits.single().vertex.id shouldBeEqualTo id("a")
    }

    @Test
    fun `bfs respects maxVertices`() {
        val visits = TreeTraverser.bfs(id("a"), adjacency, maxDepth = 10, maxVertices = 2)

        visits.forEach { log.debug { "visit=$it" } }
        visits shouldHaveSize 2
    }

    @Test
    fun `dfs visits depth first`() {
        val visits = TreeTraverser.dfs(id("a"), adjacency, maxDepth = 3, maxVertices = 100)

        visits.forEach { log.debug { "visit=$it" } }
        visits.first().vertex.id shouldBeEqualTo id("a")

        // 'a' visited first; one of b/c visited next, then descend before sibling
        visits.map { it.vertex.id.value }.toSet() shouldContainSame setOf("a", "b", "c", "d", "e")
    }
}

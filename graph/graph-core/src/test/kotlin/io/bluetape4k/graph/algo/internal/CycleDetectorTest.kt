package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import org.junit.jupiter.api.Test

class CycleDetectorTest {

    private fun id(v: String) = GraphElementId.of(v)

    @Test
    fun `DAG에서는 순환이 없다`() {
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to emptyList(),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 100)
        cycles shouldHaveSize 0
    }

    @Test
    fun `삼각 순환을 탐지한다`() {
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("a")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 100)
        cycles shouldHaveSize 1
        cycles.first().size shouldBeEqualTo 4 // a, b, c, a
        cycles.first().first() shouldBeEqualTo cycles.first().last()
    }

    @Test
    fun `maxDepth보다 긴 순환은 탐지하지 않는다`() {
        // a -> b -> c -> d -> a (간선 4개)
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("d")),
            id("d") to listOf(id("a")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 3, maxCycles = 100)
        cycles shouldHaveSize 0
    }

    @Test
    fun `maxCycles를 초과하면 반환 목록을 maxCycles로 제한한다`() {
        // 각 정점의 self-loop = 각각 독립적인 순환
        val adjacency = mapOf(
            id("a") to listOf(id("a")),
            id("b") to listOf(id("b")),
            id("c") to listOf(id("c")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 2)
        cycles shouldHaveSize 2
    }

    @Test
    fun `빈 인접 리스트는 순환 없음을 반환한다`() {
        val result = CycleDetector.findCycles(emptyMap(), maxDepth = 5, maxCycles = 10)
        result.shouldBeEmpty()
    }

    @Test
    fun `self-loop 정점은 순환으로 탐지된다`() {
        val adjacency = mapOf(id("a") to listOf(id("a")))
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 2, maxCycles = 10)
        cycles shouldHaveSize 1
        cycles[0].first() shouldBeEqualTo cycles[0].last()
    }

    @Test
    fun `회전 등가 순환은 중복 보고되지 않는다`() {
        // a→b→c→a 와 b→c→a→b 는 같은 순환 — 1회만 보고
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("a")),
        )
        val result = CycleDetector.findCycles(adjacency, maxDepth = 4, maxCycles = 10)
        result shouldHaveSize 1
    }

    @Test
    fun `maxDepth가 0 이하이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> { CycleDetector.findCycles(emptyMap(), maxDepth = 0, maxCycles = 1) }
    }

    @Test
    fun `maxCycles가 0 이하이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> { CycleDetector.findCycles(emptyMap(), maxDepth = 1, maxCycles = 0) }
    }
}

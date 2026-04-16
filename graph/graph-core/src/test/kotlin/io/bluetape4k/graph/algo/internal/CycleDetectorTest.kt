package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class CycleDetectorTest {

    private fun id(v: String) = GraphElementId.of(v)

    @Test
    fun `no cycle in DAG`() {
        val adjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to emptyList(),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 100)
        cycles shouldHaveSize 0
    }

    @Test
    fun `simple triangle cycle`() {
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
    fun `respects maxDepth`() {
        // long cycle a -> b -> c -> d -> a (length 4)
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
    fun `respects maxCycles`() {
        // self-loops on multiple vertices = many cycles
        val adjacency = mapOf(
            id("a") to listOf(id("a")),
            id("b") to listOf(id("b")),
            id("c") to listOf(id("c")),
        )
        val cycles = CycleDetector.findCycles(adjacency, maxDepth = 5, maxCycles = 2)
        cycles shouldHaveSize 2
    }
}

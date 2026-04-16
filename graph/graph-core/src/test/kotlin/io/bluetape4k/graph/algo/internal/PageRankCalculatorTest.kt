package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PageRankCalculatorTest {

    private fun id(v: String) = GraphElementId.of(v)

    @Test
    fun `single node has full pagerank mass`() {
        val scores = PageRankCalculator.compute(
            vertices = setOf(id("a")),
            outAdjacency = mapOf(id("a") to emptyList()),
            iterations = 20,
            dampingFactor = 0.85,
            tolerance = 1e-4,
        )
        scores.size shouldBeEqualTo 1
        abs(scores.getValue(id("a")) - 1.0) shouldBeGreaterThan -0.001
    }

    @Test
    fun `hub node has highest pagerank in star`() {
        // a, b, c, d all point to e
        val outAdjacency = mapOf(
            id("a") to listOf(id("e")),
            id("b") to listOf(id("e")),
            id("c") to listOf(id("e")),
            id("d") to listOf(id("e")),
            id("e") to emptyList(),
        )
        val scores = PageRankCalculator.compute(
            vertices = outAdjacency.keys,
            outAdjacency = outAdjacency,
            iterations = 50,
            dampingFactor = 0.85,
            tolerance = 1e-6,
        )
        val maxId = scores.maxByOrNull { it.value }!!.key
        maxId shouldBeEqualTo id("e")
    }

    @Test
    fun `scores sum approximately 1`() {
        val outAdjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("a")),
        )
        val scores = PageRankCalculator.compute(
            vertices = outAdjacency.keys,
            outAdjacency = outAdjacency,
            iterations = 50,
            dampingFactor = 0.85,
            tolerance = 1e-6,
        )
        val sum = scores.values.sum()
        (abs(sum - 1.0) < 0.01) shouldBeEqualTo true
    }
}

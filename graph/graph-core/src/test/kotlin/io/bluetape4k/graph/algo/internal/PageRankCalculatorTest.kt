package io.bluetape4k.graph.algo.internal

import io.bluetape4k.assertions.DOUBLE_EPSILON
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeLessThan
import io.bluetape4k.assertions.shouldBeNear
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotNull
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PageRankCalculatorTest: AbstractGraphTest() {

    companion object: KLogging() {

        private fun compute(
            vertices: Set<GraphElementId>,
            outAdjacency: Map<GraphElementId, List<GraphElementId>>,
            iterations: Int = 50,
            dampingFactor: Double = 0.85,
            tolerance: Double = DOUBLE_EPSILON,
        ): Map<GraphElementId, Double> =
            PageRankCalculator.compute(vertices, outAdjacency, iterations, dampingFactor, tolerance)
    }

    // ─── 기본 케이스 ──────────────────────────────────────────────────────────────

    @Test
    fun `단일 노드는 전체 PageRank 질량을 가진다`() {
        val scores = compute(
            vertices = setOf(id("a")),
            outAdjacency = mapOf(id("a") to emptyList()),
        )
        log.debug { "scores=$scores" }
        scores.size shouldBeEqualTo 1
        scores.getValue(id("a")).shouldBeNear(1.0, DOUBLE_EPSILON)
    }

    @Test
    fun `star 구조에서 허브 노드가 가장 높은 PageRank를 가진다`() {
        // a, b, c, d 모두 e를 가리킨다
        val outAdjacency = mapOf(
            id("a") to listOf(id("e")),
            id("b") to listOf(id("e")),
            id("c") to listOf(id("e")),
            id("d") to listOf(id("e")),
            id("e") to emptyList(),
        )
        val scores = compute(vertices = outAdjacency.keys, outAdjacency = outAdjacency)
        log.debug { "scores=$scores" }

        val maxId = scores
            .maxByOrNull { it.value }
            .requireNotNull { "scores map이 비어 있어 최대값 정점을 결정할 수 없다" }
            .key

        log.debug { "maxId=$maxId" }
        maxId shouldBeEqualTo id("e")
    }

    @Test
    fun `점수 합계는 1에 근사한다`() {
        val outAdjacency = mapOf(
            id("a") to listOf(id("b")),
            id("b") to listOf(id("c")),
            id("c") to listOf(id("a")),
        )
        val scores = compute(vertices = outAdjacency.keys, outAdjacency = outAdjacency)
        log.debug { "scores=$scores" }
        val sum = scores.values.sum()
        sum.shouldBeNear(1.0, DOUBLE_EPSILON)
    }

    // ─── precondition 검증 ────────────────────────────────────────────────────────

    @Test
    fun `빈 정점 집합은 빈 맵을 반환한다`() {
        val result = compute(vertices = emptySet(), outAdjacency = emptyMap())
        result.shouldBeEmpty()
    }

    @Test
    fun `iterations가 0 이하이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), iterations = 0)
        }
    }

    @Test
    fun `dampingFactor가 1 초과이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), dampingFactor = 1.5)
        }
    }

    @Test
    fun `dampingFactor가 음수이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), dampingFactor = -0.1)
        }
    }

    @Test
    fun `tolerance가 0 이하이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), tolerance = 0.0)
        }
    }

    // ─── dangling node ────────────────────────────────────────────────────────────

    @Test
    fun `dangling node 질량이 다른 정점에 분배되어 허브보다 높은 점수를 받는다`() {
        // a → b, b는 dangling (out-degree=0); b는 모든 질량을 받는다
        val outAdj = mapOf(
            id("a") to listOf(id("b")),
            id("b") to emptyList(),
        )
        val scores = compute(vertices = outAdj.keys, outAdjacency = outAdj)
        log.debug { "scores=$scores" }
        scores.getValue(id("b")) shouldBeGreaterThan scores.getValue(id("a"))
    }

    // ─── 비수렴 경계 케이스 ────────────────────────────────────────────────────────

    @Test
    fun `반복 횟수 소진 시 부분 결과를 반환한다`() {
        val verts = setOf(id("a"), id("b"))
        val outAdj = mapOf(id("a") to listOf(id("b")), id("b") to listOf(id("a")))

        // iterations=1, tolerance=극소 → 1회 반복으로 수렴 불가
        val scores = compute(vertices = verts, outAdjacency = outAdj, iterations = 1, tolerance = 1e-12)
        log.debug { "scores=$scores" }

        // 수렴 없어도 결과 맵은 반환됨
        scores shouldHaveSize 2
        val sum = scores.values.sum()
        sum.shouldBeNear(1.0, DOUBLE_EPSILON)  // 아직 완전 정규화 전이라도 합≈1
        abs(sum - 1.0) shouldBeLessThan 0.01 // 아직 완전 정규화 전이라도 합≈1
    }
}

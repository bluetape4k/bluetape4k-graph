package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.assertions.invoking
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeLessThan
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PageRankCalculatorTest {

    private fun id(v: String) = GraphElementId.of(v)

    private fun compute(
        vertices: Set<GraphElementId>,
        outAdjacency: Map<GraphElementId, List<GraphElementId>>,
        iterations: Int = 50,
        dampingFactor: Double = 0.85,
        tolerance: Double = 1e-6,
    ) = PageRankCalculator.compute(vertices, outAdjacency, iterations, dampingFactor, tolerance)

    // ─── 기본 케이스 ──────────────────────────────────────────────────────────────

    @Test
    fun `단일 노드는 전체 PageRank 질량을 가진다`() {
        val scores = compute(
            vertices = setOf(id("a")),
            outAdjacency = mapOf(id("a") to emptyList()),
        )
        scores.size shouldBeEqualTo 1
        abs(scores.getValue(id("a")) - 1.0) shouldBeLessThan 0.001
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
        val maxId = requireNotNull(scores.maxByOrNull { it.value }) {
            "scores map이 비어 있어 최대값 정점을 결정할 수 없다"
        }.key
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
        val sum = scores.values.sum()
        (abs(sum - 1.0) < 0.01) shouldBeEqualTo true
    }

    // ─── precondition 검증 ────────────────────────────────────────────────────────

    @Test
    fun `빈 정점 집합은 빈 맵을 반환한다`() {
        val result = compute(vertices = emptySet(), outAdjacency = emptyMap())
        result.shouldBeEmpty()
    }

    @Test
    fun `iterations가 0 이하이면 IllegalArgumentException을 던진다`() {
        invoking {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), iterations = 0)
        } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `dampingFactor가 1 초과이면 IllegalArgumentException을 던진다`() {
        invoking {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), dampingFactor = 1.5)
        } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `dampingFactor가 음수이면 IllegalArgumentException을 던진다`() {
        invoking {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), dampingFactor = -0.1)
        } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `tolerance가 0 이하이면 IllegalArgumentException을 던진다`() {
        invoking {
            compute(vertices = setOf(id("a")), outAdjacency = emptyMap(), tolerance = 0.0)
        } shouldThrow IllegalArgumentException::class
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
        scores.getValue(id("b")) shouldBeGreaterThan scores.getValue(id("a"))
    }

    // ─── 비수렴 경계 케이스 ────────────────────────────────────────────────────────

    @Test
    fun `반복 횟수 소진 시 부분 결과를 반환한다`() {
        val verts = setOf(id("a"), id("b"))
        val outAdj = mapOf(id("a") to listOf(id("b")), id("b") to listOf(id("a")))
        // iterations=1, tolerance=극소 → 1회 반복으로 수렴 불가
        val scores = compute(vertices = verts, outAdjacency = outAdj, iterations = 1, tolerance = 1e-12)

        // 수렴 없어도 결과 맵은 반환됨
        scores.size shouldBeEqualTo 2
        val sum = scores.values.sum()
        abs(sum - 1.0) shouldBeLessThan 0.01 // 아직 완전 정규화 전이라도 합≈1
    }
}

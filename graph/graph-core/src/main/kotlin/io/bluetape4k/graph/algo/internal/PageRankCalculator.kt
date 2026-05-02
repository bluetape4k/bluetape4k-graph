package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import kotlin.math.abs


/**
 * 정규화된 PageRank 반복 계산기 (JVM 폴백).
 *
 * 결과 점수의 합 ≈ 1.0 으로 정규화된다.
 * dangling node (out-degree 0)의 질량은 다음 반복 시 모든 정점에 균등 분배된다.
 *
 * ### 사용 예제
 * ```kotlin
 * val scores = PageRankCalculator.compute(
 *     vertices = vertexIds,
 *     outAdjacency = adjacency,
 *     iterations = 20,
 *     dampingFactor = 0.85,
 *     tolerance = 1e-4,
 * )
 * ```
 */
object PageRankCalculator : KLogging() {

    private const val HASH_LOAD_FACTOR = 0.75

    /**
     * 정규화된 PageRank 점수를 계산한다.
     *
     * @param vertices 전체 정점 ID 집합.
     * @param outAdjacency out-edge 인접 리스트. 집합에 없는 정점을 참조해서는 안 된다.
     * @param iterations 최대 반복 횟수. 양수여야 한다.
     * @param dampingFactor 감쇠 계수. [0.0, 1.0] 범위. 보통 0.85.
     * @param tolerance 조기 종료 L1-norm 허용치. 양수여야 한다.
     * @return 정점별 PageRank 점수 맵. 합계 ≈ 1.0.
     */
    fun compute(
        vertices: Set<GraphElementId>,
        outAdjacency: Map<GraphElementId, List<GraphElementId>>,
        iterations: Int,
        dampingFactor: Double,
        tolerance: Double,
    ): Map<GraphElementId, Double> {
        require(iterations > 0) { "iterations must be > 0, was $iterations" }
        require(dampingFactor in 0.0..1.0) { "dampingFactor must be in [0, 1], was $dampingFactor" }
        require(tolerance > 0.0) { "tolerance must be > 0.0, was $tolerance" }

        if (vertices.isEmpty()) return emptyMap()

        val n = vertices.size
        // HashMap rehash 방지: Double 나눗셈으로 정밀도 확보 (0.75f 는 n≥25M 에서 under-provision)
        val mapCapacity = ((n / HASH_LOAD_FACTOR) + 1).toInt()
        val initial = 1.0 / n
        var ranks = HashMap<GraphElementId, Double>(mapCapacity)
        vertices.forEach { ranks[it] = initial }

        // dangling node 집합은 그래프 구조가 고정되므로 루프 밖에서 1회 계산
        val danglingNodes = vertices.filter { outAdjacency[it].isNullOrEmpty() }

        repeat(iterations) {
            val newRanks = HashMap<GraphElementId, Double>(mapCapacity)
            // base teleport probability
            val baseRank = (1.0 - dampingFactor) / n

            val danglingMass = danglingNodes.sumOf { ranks.getOrDefault(it, 0.0) }
            val danglingShare = dampingFactor * danglingMass / n

            vertices.forEach { v -> newRanks[v] = baseRank + danglingShare }

            vertices.forEach { src ->
                val outs = outAdjacency[src].orEmpty()
                if (outs.isNotEmpty()) {
                    val share = dampingFactor * ranks.getOrDefault(src, 0.0) / outs.size
                    outs.forEach { dst ->
                        // merge로 단일 해시 탐색으로 읽기+쓰기 처리 (이중 맵 조회 방지)
                        newRanks.merge(dst, share, Double::plus)
                    }
                }
            }

            val delta = vertices.sumOf { abs((newRanks[it] ?: 0.0) - (ranks[it] ?: 0.0)) }
            ranks = newRanks
            if (delta < tolerance) return ranks
        }

        log.warn { "PageRank did not converge after $iterations iterations (vertices=${vertices.size})" }
        return ranks
    }
}

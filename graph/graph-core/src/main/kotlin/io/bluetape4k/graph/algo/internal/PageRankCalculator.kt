package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
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
object PageRankCalculator {

    /**
     * @param vertices 전체 정점 ID 집합.
     * @param outAdjacency out-edge 인접 리스트.
     * @param iterations 최대 반복 횟수.
     * @param dampingFactor 감쇠 계수 (보통 0.85).
     * @param tolerance L1-norm 수렴 허용치.
     */
    fun compute(
        vertices: Set<GraphElementId>,
        outAdjacency: Map<GraphElementId, List<GraphElementId>>,
        iterations: Int,
        dampingFactor: Double,
        tolerance: Double,
    ): Map<GraphElementId, Double> {
        if (vertices.isEmpty()) return emptyMap()

        val n = vertices.size
        val initial = 1.0 / n
        var ranks = HashMap<GraphElementId, Double>(n)
        vertices.forEach { ranks[it] = initial }

        repeat(iterations) {
            val newRanks = HashMap<GraphElementId, Double>(n)
            // base teleport probability
            val baseRank = (1.0 - dampingFactor) / n

            // dangling mass — sum of ranks for vertices with no outgoing edges
            val danglingMass = vertices.filter { outAdjacency[it].isNullOrEmpty() }
                .sumOf { ranks.getOrDefault(it, 0.0) }
            val danglingShare = dampingFactor * danglingMass / n

            vertices.forEach { v -> newRanks[v] = baseRank + danglingShare }

            vertices.forEach { src ->
                val outs = outAdjacency[src].orEmpty()
                if (outs.isNotEmpty()) {
                    val share = dampingFactor * ranks.getOrDefault(src, 0.0) / outs.size
                    outs.forEach { dst ->
                        newRanks[dst] = (newRanks[dst] ?: 0.0) + share
                    }
                }
            }

            val delta = vertices.sumOf { abs((newRanks[it] ?: 0.0) - (ranks[it] ?: 0.0)) }
            ranks = newRanks
            if (delta < tolerance) return ranks
        }
        return ranks
    }
}

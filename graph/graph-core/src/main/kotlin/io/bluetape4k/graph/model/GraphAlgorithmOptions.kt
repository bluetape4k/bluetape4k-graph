package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Analytics 알고리즘 옵션의 공통 sealed 클래스.
 *
 * `maxDepth` 개념이 없는 알고리즘(PageRank / Degree / ConnectedComponents) 전용이다.
 * 탐색 깊이가 의미 있는 알고리즘은 [GraphTraversalOptions] 하위를 사용한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val opts: GraphAlgorithmOptions = PageRankOptions(iterations = 20)
 * ```
 */
sealed class GraphAlgorithmOptions: Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * PageRank 옵션.
 *
 * @param vertexLabel `null` 이면 전체 정점 대상.
 * @param edgeLabel `null` 이면 모든 간선 포함.
 * @param iterations 반복 횟수 (기본 20).
 * @param dampingFactor 감쇠 인수 (기본 0.85). 백엔드별 지원 여부 상이.
 * @param tolerance 수렴 허용 오차 (기본 1e-4). 백엔드별 지원 여부 상이.
 * @param topK 상위 K개 결과만 반환. `Int.MAX_VALUE` = 전체 반환.
 *
 * 결과 순서: score 내림차순 정렬 보장.
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = PageRankOptions(vertexLabel = "Person", iterations = 30, topK = 10)
 * val top10 = ops.pageRank(opts)
 * ```
 */
data class PageRankOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    val iterations: Int = 20,
    val dampingFactor: Double = 0.85,
    val tolerance: Double = 1e-4,
    val topK: Int = Int.MAX_VALUE,
): GraphAlgorithmOptions() {
    init {
        require(iterations > 0) { "iterations must be > 0, was $iterations" }
        require(topK > 0) { "topK must be > 0, was $topK" }
        require(dampingFactor in 0.0..1.0) { "dampingFactor must be in [0,1], was $dampingFactor" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PageRankOptions()
    }
}

/**
 * Degree Centrality 옵션.
 *
 * @param edgeLabel `null` 이면 모든 간선 포함.
 * @param direction 방향 (BOTH / OUTGOING / INCOMING).
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = DegreeOptions(edgeLabel = "KNOWS", direction = Direction.BOTH)
 * val degree = ops.degreeCentrality(alice.id, opts)
 * ```
 */
data class DegreeOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.BOTH,
): GraphAlgorithmOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = DegreeOptions()
    }
}

/**
 * Connected Components 옵션.
 *
 * @param vertexLabel `null` 이면 전체 정점.
 * @param edgeLabel `null` 이면 모든 간선.
 * @param weakly `true` = Weakly Connected (방향 무시), `false` = Strongly Connected.
 * @param minSize 반환할 최소 컴포넌트 크기 (기본 1).
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = ComponentOptions(weakly = true, minSize = 2)
 * val components = ops.connectedComponents(opts)
 * ```
 */
data class ComponentOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    val weakly: Boolean = true,
    val minSize: Int = 1,
): GraphAlgorithmOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = ComponentOptions()
    }
}

package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * PageRank 점수 한 개를 나타내는 결과 모델.
 *
 * 결과 목록은 score 내림차순 정렬이 보장된다.
 * `Flow<PageRankScore>` 도 동일 순서로 emit 된다.
 *
 * @property vertex 점수를 가진 정점.
 * @property score 정점의 PageRank 점수 (0.0 이상).
 *
 * ### 사용 예제
 * ```kotlin
 * val scores = ops.pageRank(PageRankOptions(iterations = 20))
 * val top = scores.first()
 * println("${top.vertex.label}: ${top.score}")
 * ```
 */
data class PageRankScore(
    val vertex: GraphVertex,
    val score: Double,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

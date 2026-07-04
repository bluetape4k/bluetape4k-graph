package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Result model for one PageRank score.
 *
 * Result lists are ordered by descending score.
 * `Flow<PageRankScore>` emits values in the same order.
 *
 * @property vertex Vertex that owns the score.
 * @property score PageRank score for the vertex, at least `0.0`.
 *
 * ### Usage
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

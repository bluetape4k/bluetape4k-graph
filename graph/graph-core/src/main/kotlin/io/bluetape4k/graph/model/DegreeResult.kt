package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Degree Centrality(연결 중심성) 결과.
 *
 * @property vertexId 측정 대상 정점 ID.
 * @property inDegree 들어오는 간선 수.
 * @property outDegree 나가는 간선 수.
 *
 * ### 사용 예제
 * ```kotlin
 * val degree = ops.degreeCentrality(alice.id)
 * println("in=${degree.inDegree} out=${degree.outDegree} total=${degree.total}")
 * ```
 */
data class DegreeResult(
    val vertexId: GraphElementId,
    val inDegree: Int,
    val outDegree: Int,
): Serializable {
    /** in + out 간선 수 합계. */
    val total: Int get() = inDegree + outDegree

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Degree centrality 결과.
 *
 * @property vertexId 측정 대상 vertex의 ID.
 * @property inDegree 들어오는 edge 수.
 * @property outDegree 나가는 edge 수.
 *
 * ### Usage
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
    /** Sum of incoming and outgoing edges. */
    val total: Int get() = inDegree + outDegree

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

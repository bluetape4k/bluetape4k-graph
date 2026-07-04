package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Degree centrality result.
 *
 * @property vertexId ID of the measured vertex.
 * @property inDegree Number of incoming edges.
 * @property outDegree Number of outgoing edges.
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

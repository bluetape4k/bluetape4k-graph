package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * batch edge 생성을 위한 input model.
 *
 * A single [GraphEdgeRepository.createEdges][io.bluetape4k.graph.repository.GraphEdgeRepository.createEdges]
 * 호출은 하나의 edge label을 공유하므로, 이 model은 endpoint와 row별 property만 가진다.
 *
 * ## Contract
 *
 * - [fromId] and [toId] must identify existing vertices.
 * - [properties] is the property map stored on the created edge.
 * - Batch insert is not merge/upsert; repeated inputs can create duplicate edges.
 *
 * ```kotlin
 * val edge = BatchEdge(
 *     fromId = alice.id,
 *     toId = bob.id,
 *     properties = mapOf("since" to 2024),
 * )
 * ```
 */
data class BatchEdge(
    val fromId: GraphElementId,
    val toId: GraphElementId,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L

        operator fun invoke(
            fromId: GraphElementId,
            toId: GraphElementId,
            vararg params: Pair<String, Any?>,
        ): BatchEdge {
            return BatchEdge(fromId, toId, params.toMap())
        }
    }
}

fun batchEdgeOf(
    fromId: GraphElementId,
    toId: GraphElementId,
    properties: Map<String, Any?> = emptyMap(),
): BatchEdge =
    BatchEdge(fromId, toId, properties)

fun batchEdgeOf(
    fromId: GraphElementId,
    toId: GraphElementId,
    vararg params: Pair<String, Any?>,
): BatchEdge =
    BatchEdge(fromId, toId, params.toMap())

fun batchEdgeOf(
    fromId: Any,
    toId: Any,
    properties: Map<String, Any?> = emptyMap(),
): BatchEdge =
    BatchEdge(fromId.toGraphElementId(), toId.toGraphElementId(), properties)

fun batchEdgeOf(
    fromId: Any,
    toId: Any,
    vararg params: Pair<String, Any?>,
): BatchEdge =
    BatchEdge(fromId.toGraphElementId(), toId.toGraphElementId(), params.toMap())

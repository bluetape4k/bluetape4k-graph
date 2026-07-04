package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Input model for batch edge creation.
 *
 * A single [GraphEdgeRepository.createEdges][io.bluetape4k.graph.repository.GraphEdgeRepository.createEdges]
 * call shares one edge label, so this model carries only the endpoints and per-row properties.
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
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

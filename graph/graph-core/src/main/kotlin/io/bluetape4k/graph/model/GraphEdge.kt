package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Graph edge or relationship.
 *
 * Immutable model for a relationship between two vertices.
 * In directed graphs, the edge points from [startId] to [endId].
 * Self-loop edges with `startId == endId` are allowed.
 *
 * @property id Backend-independent edge ID.
 * @property label Label that describes the relationship type, such as `"KNOWS"` or `"WORKS_AT"`.
 * @property startId Start vertex ID.
 * @property endId End vertex ID.
 * @property properties Property map attached to the edge. Values may contain `null`.
 *
 * ### Usage
 * ```kotlin
 * val edge = GraphEdge(
 *     id = GraphElementId.of("e-1"),
 *     label = "KNOWS",
 *     startId = GraphElementId.of("v-1"),
 *     endId = GraphElementId.of("v-2"),
 *     properties = mapOf("since" to 2023)
 * )
 * ```
 */
data class GraphEdge(
    val id: GraphElementId,
    val label: String,
    val startId: GraphElementId,
    val endId: GraphElementId,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

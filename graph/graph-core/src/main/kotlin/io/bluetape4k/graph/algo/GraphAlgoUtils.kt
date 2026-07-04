package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId

/**
 * Computes the neighboring vertex ID shared by [DijkstraRunner] and [AStarRunner].
 *
 * @param currentId currently visited vertex ID.
 * @param edge edge to inspect.
 * @param direction traversal direction.
 * @return neighboring vertex ID, or `null` when the edge does not match [direction].
 */
internal fun neighbourId(
    currentId: GraphElementId,
    edge: GraphEdge,
    direction: Direction,
): GraphElementId? = when (direction) {
    Direction.OUTGOING -> if (edge.startId == currentId) edge.endId else null
    Direction.INCOMING -> if (edge.endId == currentId) edge.startId else null
    Direction.BOTH -> when (currentId) {
        edge.startId -> edge.endId
        edge.endId -> edge.startId
        else -> null
    }
}

package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId

/**
 * [DijkstraRunner]와 [AStarRunner]가 공유하는 neighboring vertex ID를 계산한다.
 *
 * @param currentId 현재 방문 중인 vertex ID.
 * @param edge 검사할 edge.
 * @param direction traversal direction.
 * @return neighboring vertex ID. edge가 [direction]과 맞지 않으면 `null`.
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

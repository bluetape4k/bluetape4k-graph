package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId

/**
 * DijkstraRunner와 AStarRunner가 공유하는 이웃 정점 ID 계산 함수.
 *
 * @param currentId 현재 탐색 중인 정점 ID.
 * @param edge 검사할 간선.
 * @param direction 탐색 방향.
 * @return 이웃 정점 ID. 방향 조건 미충족 시 null.
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

package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep

/**
 * 탐색 완료 후 predecessor 맵에서 [GraphPath]를 역추적하여 재구성한다.
 *
 * Dijkstra/A* 탐색이 끝난 뒤 `cameFrom` 테이블에서 경로를 역추적한다.
 * `totalWeight`는 탐색 과정에서 계산된 누적 비용을 그대로 사용한다.
 *
 * @param targetId 도착 정점 ID.
 * @param cameFrom 정점 ID → (부모 정점, 연결 간선) 맵.
 * @param vertexLookup 정점 ID → [GraphVertex] 조회 함수.
 * @param totalWeight 경로의 총 가중치.
 * @return 재구성된 [GraphPath]. 경로가 없으면 `null`.
 */
internal fun reconstructPath(
    targetId: GraphElementId,
    cameFrom: Map<GraphElementId, Pair<GraphVertex, GraphEdge>>,
    vertexLookup: (GraphElementId) -> GraphVertex?,
    totalWeight: Double,
): GraphPath? {
    val steps = mutableListOf<PathStep>()
    var currentId = targetId

    while (true) {
        val entry = cameFrom[currentId] ?: break
        val (parentVertex, edge) = entry
        steps.add(PathStep.VertexStep(vertexLookup(currentId) ?: return null))
        steps.add(PathStep.EdgeStep(edge))
        currentId = parentVertex.id
    }

    // 출발 정점 추가
    val startVertex = vertexLookup(currentId) ?: return null
    steps.add(PathStep.VertexStep(startVertex))

    steps.reverse()
    return GraphPath(steps = steps, totalWeight = totalWeight)
}

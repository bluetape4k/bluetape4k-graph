package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.util.*

/**
 * Dijkstra 알고리즘으로 단일 출발지 최단 경로를 계산한다.
 *
 * ## 설계 결정
 * - **JVM 단일 구현**: 모든 백엔드(Neo4j/AGE/FalkorDB/TinkerPop)가 이 구현에 위임한다.
 * - **결정적 tie-break**: 동일 비용 정점은 ID 사전순으로 정렬해 비결정적 동작을 방지한다.
 * - **maxVisited 보호**: 무한 그래프에서의 무한 확장을 방지한다.
 * - **Direction 지원**: OUTGOING, INCOMING, BOTH 모두 지원한다.
 *
 * @param fetchEdges 정점 ID → 인접 간선 목록 조회 함수. [Direction]은 호출 전 적용되어야 한다.
 * @param fetchVertex 정점 ID → [GraphVertex] 조회 함수.
 */
class DijkstraRunner(
    private val fetchEdges: (GraphElementId) -> List<GraphEdge>,
    private val fetchVertex: (GraphElementId) -> GraphVertex?,
) {
    companion object : KLogging()

    /**
     * Dijkstra 알고리즘으로 [fromId] → [toId] 최단 경로를 계산한다.
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (weightProperty, missingWeightPolicy, maxVisited).
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     */
    fun run(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val weightProperty = requireNotNull(options.weightProperty) {
            "PathOptions.weightProperty must be set for Dijkstra"
        }
        val extractor = WeightExtractor(weightProperty, options.missingWeightPolicy)

        // (cost, vertexId) — tie-break은 vertexId 사전순
        val pq = PriorityQueue(
            compareBy<Pair<Double, GraphElementId>> { it.first }
                .thenComparing { a, b -> a.second.value.compareTo(b.second.value) }
        )
        val dist = mutableMapOf<GraphElementId, Double>()
        val cameFrom = mutableMapOf<GraphElementId, Pair<GraphVertex, GraphEdge>>()

        dist[fromId] = 0.0
        pq.add(0.0 to fromId)
        var visited = 0

        while (pq.isNotEmpty()) {
            val (cost, currentId) = pq.poll()

            if (cost > (dist[currentId] ?: Double.MAX_VALUE)) continue // stale entry

            if (currentId == toId) {
                log.debug { "Dijkstra found path: $fromId → $toId, cost=$cost, visited=$visited" }
                return reconstructPath(toId, cameFrom, { fetchVertex(it) }, cost)
            }

            if (++visited > options.maxVisited) {
                log.debug { "Dijkstra maxVisited=${options.maxVisited} reached; no path found" }
                break
            }

            val edges = fetchEdges(currentId)

            for (edge in edges.sortedBy { it.id.value }) {
                val neighborId = neighbourId(currentId, edge, options.direction) ?: continue
                val w = extractor.extract(edge) ?: continue // Skip 정책

                val newCost = cost + w
                if (newCost < (dist[neighborId] ?: Double.MAX_VALUE)) {
                    dist[neighborId] = newCost
                    val currentVertex = fetchVertex(currentId) ?: continue
                    cameFrom[neighborId] = currentVertex to edge
                    pq.add(newCost to neighborId)
                }
            }
        }

        return null
    }

    private fun neighbourId(
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
}

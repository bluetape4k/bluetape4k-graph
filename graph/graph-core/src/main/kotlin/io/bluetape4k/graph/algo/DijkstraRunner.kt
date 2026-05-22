package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
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
 * ### 사용 예제
 *
 * ```kotlin
 * val runner = DijkstraRunner(
 *     fetchEdges = { id -> ops.findEdgesByStartId(id) },
 *     fetchVertex = { id -> ops.findVertexById(id) },
 * )
 *
 * val path: GraphPath? = runner.run(
 *     fromId = GraphElementId("A"),
 *     toId = GraphElementId("Z"),
 *     options = PathOptions(
 *         weightProperty = "cost",
 *         missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
 *         maxVisited = 100_000,
 *     ),
 * )
 *
 * path?.let { println("총 비용=${it.totalWeight}, 경로 길이=${it.steps.size}") }
 * ```
 *
 * @param fetchEdges 정점 ID → 인접 간선 목록 조회 함수. `Direction`은 호출 전 적용되어야 한다.
 * @param fetchVertex 정점 ID → [GraphVertex] 조회 함수.
 */
class DijkstraRunner(
    private val fetchEdges: (GraphElementId) -> List<GraphEdge>,
    private val fetchVertex: (GraphElementId) -> GraphVertex?,
) {
    companion object : KLogging()

    /** PriorityQueue entry with deterministic tie-break and no Pair comparator dispatch. */
    private data class DijkstraNode(val cost: Double, val id: GraphElementId) : Comparable<DijkstraNode> {
        override fun compareTo(other: DijkstraNode): Int {
            val cmp = cost.compareTo(other.cost)
            return if (cmp != 0) cmp else id.value.compareTo(other.id.value)
        }
    }

    /**
     * Dijkstra 알고리즘으로 [fromId] → [toId] 최단 경로를 계산한다.
     *
     * @param fromId 출발 정점 ID.
     * @param toId 도착 정점 ID.
     * @param options 탐색 옵션 (weightProperty, missingWeightPolicy, maxVisited).
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     * @throws IllegalArgumentException [PathOptions.weightProperty]가 null인 경우.
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

        // cost + vertexId tie-break keeps traversal deterministic across equal-cost frontiers.
        val pq = PriorityQueue<DijkstraNode>()
        val dist = mutableMapOf<GraphElementId, Double>()
        val cameFrom = mutableMapOf<GraphElementId, Pair<GraphVertex, GraphEdge>>()

        dist[fromId] = 0.0
        pq.add(DijkstraNode(0.0, fromId))
        var visited = 0

        outer@ while (pq.isNotEmpty()) {
            val (cost, currentId) = pq.poll()

            if (cost > (dist[currentId] ?: Double.MAX_VALUE)) continue // stale entry

            if (currentId == toId) {
                log.debug { "Dijkstra found path: $fromId → $toId, cost=$cost, visited=$visited" }
                return reconstructPath(toId, cameFrom, { fetchVertex(it) }, cost)
            }

            if (++visited > options.maxVisited) {
                log.warn { "Dijkstra maxVisited=${options.maxVisited} reached; no path found from $fromId → $toId" }
                break
            }

            val currentVertex = fetchVertex(currentId) ?: run {
                log.warn { "Dijkstra: fetchVertex($currentId) returned null mid-traversal — skipping neighbors" }
                continue@outer
            }
            val edges = fetchEdges(currentId)

            for (edge in edges) {
                val neighborId = neighbourId(currentId, edge, options.direction) ?: continue
                val w = extractor.extract(edge) ?: continue // Skip 정책

                val newCost = cost + w
                if (newCost < (dist[neighborId] ?: Double.MAX_VALUE)) {
                    dist[neighborId] = newCost
                    cameFrom[neighborId] = currentVertex to edge
                    pq.add(DijkstraNode(newCost, neighborId))
                }
            }
        }

        return null
    }
}

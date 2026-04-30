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
 * A* 알고리즘으로 휴리스틱 유도 최단 경로를 계산한다.
 *
 * ## 설계 결정
 * - [heuristic]은 동기 함수만 허용한다 (`suspend` 불가). 코루틴 컨텍스트 내에서도 동기 호출.
 * - 허용 가능(admissible) 휴리스틱: `h(n) <= 실제_비용(n, goal)`. 위반 시 최적성 보장 불가.
 * - tie-break은 DijkstraRunner와 동일하게 vertexId 사전순.
 *
 * @param fetchEdges 정점 ID → 인접 간선 목록 조회 함수.
 * @param fetchVertex 정점 ID → [GraphVertex] 조회 함수.
 * @param heuristic 목표 정점까지의 예상 비용 함수. 반드시 허용 가능해야 한다.
 */
class AStarRunner(
    private val fetchEdges: (GraphElementId) -> List<GraphEdge>,
    private val fetchVertex: (GraphElementId) -> GraphVertex?,
    private val heuristic: (GraphVertex) -> Double,
) {
    companion object : KLogging()

    /**
     * A* 알고리즘으로 [fromId] → [toId] 최단 경로를 계산한다.
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
            "PathOptions.weightProperty must be set for A*"
        }
        val extractor = WeightExtractor(weightProperty, options.missingWeightPolicy)

        // f = g + h; tie-break: vertexId 사전순
        val pq = PriorityQueue(
            compareBy<Triple<Double, Double, GraphElementId>> { it.first }
                .thenComparing { a, b -> a.third.value.compareTo(b.third.value) }
        )
        val gScore = mutableMapOf<GraphElementId, Double>()
        val cameFrom = mutableMapOf<GraphElementId, Pair<GraphVertex, GraphEdge>>()

        gScore[fromId] = 0.0
        val startVertex = fetchVertex(fromId) ?: return null
        val h0 = heuristic(startVertex)
        pq.add(Triple(h0, 0.0, fromId))
        var visited = 0

        outer@ while (pq.isNotEmpty()) {
            val (_, g, currentId) = pq.poll()

            if (g > (gScore[currentId] ?: Double.MAX_VALUE)) continue // stale

            if (currentId == toId) {
                log.debug { "A* found path: $fromId → $toId, cost=$g, visited=$visited" }
                return reconstructPath(toId, cameFrom, { fetchVertex(it) }, g)
            }

            if (++visited > options.maxVisited) {
                log.warn { "A* maxVisited=${options.maxVisited} reached; no path found from $fromId → $toId" }
                break
            }

            val currentVertex = fetchVertex(currentId) ?: run {
                log.warn { "A*: fetchVertex($currentId) returned null mid-traversal — skipping neighbors" }
                continue@outer
            }
            val edges = fetchEdges(currentId)

            for (edge in edges) {
                val neighborId = neighbourId(currentId, edge, options.direction) ?: continue
                val w = extractor.extract(edge) ?: continue

                val tentativeG = g + w
                if (tentativeG < (gScore[neighborId] ?: Double.MAX_VALUE)) {
                    gScore[neighborId] = tentativeG
                    cameFrom[neighborId] = currentVertex to edge

                    val neighbor = fetchVertex(neighborId) ?: continue
                    val f = tentativeG + heuristic(neighbor)
                    pq.add(Triple(f, tentativeG, neighborId))
                }
            }
        }

        return null
    }
}

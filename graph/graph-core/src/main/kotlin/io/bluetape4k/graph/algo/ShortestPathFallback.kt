package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphEdgeRepository
import io.bluetape4k.graph.repository.GraphOperations

/**
 * JVM 구현 기반의 가중치 최단 경로 탐색 헬퍼 (동기 전용).
 *
 * 모든 백엔드(Neo4j/Memgraph/AGE/TinkerPop/FalkorDB)의 동기 [GraphOperations]에서
 * Dijkstra/A* 최단 경로 계산에 사용된다.
 *
 * ## 사용 패턴
 *
 * **동기 백엔드:**
 * ```kotlin
 * override fun shortestPath(...): GraphPath? =
 *     if (options.weightProperty != null) ShortestPathFallback.dijkstra(this, ...)
 *     else super.shortestPath(...)
 *
 * override fun aStarPath(...): GraphPath? =
 *     ShortestPathFallback.aStar(this, ...)
 * ```
 *
 * **코루틴 백엔드** (syncDelegate 위임):
 * ```kotlin
 * override suspend fun shortestPath(...): GraphPath? =
 *     if (options.weightProperty != null) withContext(Dispatchers.IO) {
 *         ShortestPathFallback.dijkstra(syncDelegate, ...)
 *     } else ...
 * ```
 *
 * ## 인터페이스 기본 메서드를 사용하지 않는 이유
 * 탐색 알고리즘이 [GraphOperations] 전체(정점+간선 조회)를 필요로 하는데,
 * `GraphTraversalRepository` 인터페이스의 `this`는 해당 타입을 만족하지 않는다.
 * 따라서 각 백엔드 `override`에서 이 오브젝트를 직접 호출한다.
 */
object ShortestPathFallback {

    /**
     * [GraphOperations]를 사용해 Dijkstra 가중치 최단 경로를 계산한다.
     */
    fun dijkstra(
        ops: GraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val runner = DijkstraRunner(
            fetchEdges = { id -> fetchIncident(ops, id, options.edgeLabel, options.direction) },
            fetchVertex = { id -> ops.findVertexById(id) },
        )
        return runner.run(fromId, toId, options)
    }

    /**
     * [GraphOperations]를 사용해 A* 가중치 최단 경로를 계산한다.
     */
    fun aStar(
        ops: GraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): GraphPath? {
        val runner = AStarRunner(
            fetchEdges = { id -> fetchIncident(ops, id, options.edgeLabel, options.direction) },
            fetchVertex = { id -> ops.findVertexById(id) },
            heuristic = heuristic,
        )
        return runner.run(fromId, toId, options)
    }

    private fun fetchIncident(
        ops: GraphEdgeRepository,
        id: GraphElementId,
        edgeLabel: String?,
        direction: Direction,
    ): List<GraphEdge> = when (direction) {
        Direction.OUTGOING -> ops.findEdgesByStartId(id, edgeLabel)
        Direction.INCOMING -> ops.findEdgesByEndId(id, edgeLabel)
        Direction.BOTH -> (ops.findEdgesByStartId(id, edgeLabel) + ops.findEdgesByEndId(id, edgeLabel))
            .sortedBy { it.id.value }
    }
}

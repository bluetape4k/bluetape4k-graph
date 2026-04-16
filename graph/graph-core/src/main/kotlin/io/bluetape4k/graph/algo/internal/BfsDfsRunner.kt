package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.TraversalVisit
import java.util.ArrayDeque

/**
 * 인접 리스트 기반 BFS/DFS JVM 폴백 러너.
 *
 * 백엔드 native 미지원 시 사용한다 (AGE 등).
 *
 * ### 사용 예제
 * ```kotlin
 * val adjacency: Map<GraphElementId, List<GraphElementId>> = ...
 * val visits = BfsDfsRunner.bfs(start.id, adjacency, maxDepth = 3, maxVertices = 1000)
 * ```
 */
object BfsDfsRunner {

    /**
     * BFS 방문 결과를 반환한다 (레벨 순).
     *
     * @param startId 시작 정점 ID.
     * @param adjacency 인접 리스트 (out-edges).
     * @param maxDepth 최대 탐색 깊이.
     * @param maxVertices 반환할 최대 정점 수.
     * @param vertexResolver 정점 ID → [GraphVertex] 변환기 (기본: 빈 properties 정점).
     */
    fun bfs(
        startId: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxVertices: Int,
        vertexResolver: (GraphElementId) -> GraphVertex = { GraphVertex(it, "", emptyMap()) },
    ): List<TraversalVisit> {
        val visited = HashSet<GraphElementId>()
        val result = ArrayList<TraversalVisit>()
        val queue: ArrayDeque<Triple<GraphElementId, Int, GraphElementId?>> = ArrayDeque()

        queue.add(Triple(startId, 0, null))
        visited.add(startId)

        while (queue.isNotEmpty() && result.size < maxVertices) {
            val (id, depth, parentId) = queue.poll()
            result.add(TraversalVisit(vertexResolver(id), depth, parentId))
            if (depth >= maxDepth) continue

            adjacency[id].orEmpty().forEach { next ->
                if (visited.add(next)) {
                    queue.add(Triple(next, depth + 1, id))
                }
            }
        }
        return result
    }

    /**
     * DFS 방문 결과를 반환한다 (깊이 우선, pre-order).
     */
    fun dfs(
        startId: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxVertices: Int,
        vertexResolver: (GraphElementId) -> GraphVertex = { GraphVertex(it, "", emptyMap()) },
    ): List<TraversalVisit> {
        val visited = HashSet<GraphElementId>()
        val result = ArrayList<TraversalVisit>()
        val stack: ArrayDeque<Triple<GraphElementId, Int, GraphElementId?>> = ArrayDeque()

        stack.push(Triple(startId, 0, null))

        while (stack.isNotEmpty() && result.size < maxVertices) {
            val (id, depth, parentId) = stack.pop()
            if (!visited.add(id)) continue
            result.add(TraversalVisit(vertexResolver(id), depth, parentId))
            if (depth >= maxDepth) continue

            // push in reverse so first neighbor is popped first
            adjacency[id].orEmpty().asReversed().forEach { next ->
                if (next !in visited) stack.push(Triple(next, depth + 1, id))
            }
        }
        return result
    }
}

package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.model.vertexOf
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.util.*


/**
 * Adjacency-list BFS/DFS runner used as a JVM fallback.
 *
 * Used when the backend has no native traversal support.
 *
 * ### Usage
 * ```kotlin
 * val adjacency: Map<GraphElementId, List<GraphElementId>> = ...
 * val visits = TreeTraverser.bfs(start.id, adjacency, maxDepth = 3, maxVertices = 1000)
 * ```
 */
object TreeTraverser: KLogging() {

    /**
     * Returns BFS visit results in level order.
     *
     * @param startId start vertex ID.
     * @param adjacency adjacency list of out-edges.
     * @param maxDepth maximum traversal depth.
     * @param maxVertices maximum number of vertices to return.
     * @param vertexResolver vertex ID to [GraphVertex] resolver, defaulting to empty-property vertices.
     */
    fun bfs(
        startId: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxVertices: Int,
        vertexResolver: (GraphElementId) -> GraphVertex = { vertexOf(it, "") },
    ): List<TraversalVisit> {
        val visited = HashSet<GraphElementId>()
        val result = ArrayList<TraversalVisit>()
        val queue: ArrayDeque<Triple<GraphElementId, Int, GraphElementId?>> = ArrayDeque()

        queue.add(Triple(startId, 0, null))
        visited.add(startId)

        while (queue.isNotEmpty() && result.size < maxVertices) {
            val (id, depth, parentId) = queue.poll()
            val visitElement = TraversalVisit(vertexResolver(id), depth, parentId)
            log.debug { "visit depth=$depth index=${result.size + 1}" }
            result.add(visitElement)

            if (depth >= maxDepth) {
                log.debug { "depth[$depth] 가 maxDepth[$maxDepth] 와 같거나 이상입니다. 다음 depth 탐색은 중단합니다." }
                continue
            }

            adjacency[id].orEmpty().forEach { next ->
                if (visited.add(next)) {
                    queue += Triple(next, depth + 1, id)
                }
            }
        }
        return result
    }

    /**
     * Returns DFS visit results in depth-first preorder.
     */
    fun dfs(
        startId: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxVertices: Int,
        vertexResolver: (GraphElementId) -> GraphVertex = { vertexOf(it, "") },
    ): List<TraversalVisit> {
        val visited = HashSet<GraphElementId>()
        val result = ArrayList<TraversalVisit>()
        val stack: ArrayDeque<Triple<GraphElementId, Int, GraphElementId?>> = ArrayDeque()

        stack.push(Triple(startId, 0, null))

        while (stack.isNotEmpty() && result.size < maxVertices) {
            val (id, depth, parentId) = stack.pop()
            if (!visited.add(id)) continue

            val visitElement = TraversalVisit(vertexResolver(id), depth, parentId)
            log.debug { "visit depth=$depth index=${result.size + 1}" }
            result.add(visitElement)

            if (depth >= maxDepth) {
                log.debug {
                    "depth[$depth] 가 maxDepth[$maxDepth] 와 같거나 이상입니다. " +
                        "다음 depth 탐색은 중단합니다."
                }
            } else {
                // push in reverse so first neighbor is popped first
                adjacency[id].orEmpty().asReversed().forEach { next ->
                    if (next !in visited) stack.push(Triple(next, depth + 1, id))
                }
            }
        }
        return result
    }
}

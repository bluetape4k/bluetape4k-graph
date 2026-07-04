package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * BFS / DFS visit event.
 *
 * Represents the visited vertex, depth, and parent vertex at traversal time.
 *
 * @property vertex Visited vertex.
 * @property depth Depth from the start vertex. The start vertex is depth `0`.
 * @property parentId Previous vertex ID. `null` for the start vertex.
 *
 * ### Usage
 * ```kotlin
 * val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3))
 * visits.forEach { println("d=${it.depth} v=${it.vertex.label}") }
 * ```
 */
data class TraversalVisit(
    val vertex: GraphVertex,
    val depth: Int,
    val parentId: GraphElementId?,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

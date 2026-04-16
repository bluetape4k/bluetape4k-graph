package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * BFS / DFS 방문 이벤트.
 *
 * 탐색 시점의 정점, 깊이, 부모 정점을 표현한다.
 *
 * @property vertex 방문한 정점.
 * @property depth 시작 정점으로부터의 깊이 (시작 정점 = 0).
 * @property parentId 직전 정점의 ID. 시작 정점은 `null`.
 *
 * ### 사용 예제
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

package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프 순회 옵션의 기반 sealed class.
 *
 * ### 사용 예제
 * ```kotlin
 * // 1단계 OUTGOING 이웃 탐색
 * val neighborOpts = NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING)
 *
 * // 최대 5홉의 최단/전체 경로 탐색
 * val pathOpts = PathOptions(edgeLabel = "KNOWS", maxDepth = 5)
 * ```
 */
sealed class GraphTraversalOptions: Serializable {
    /**
     * 최대 탐색 깊이. 서브클래스별 기본값이 다르다 ([NeighborOptions]: 1, [PathOptions]: 10).
     *
     * ```kotlin
     * val opts = NeighborOptions(maxDepth = 3)  // 3홉까지 탐색
     * ```
     */
    abstract val maxDepth: Int
}

/**
 * [GraphTraversalRepository.neighbors] 호출 옵션.
 *
 * ```kotlin
 * val opts = NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
 * val friends = ops.neighbors(alice.id, opts)
 * ```
 *
 * @param edgeLabel 탐색할 엣지 레이블. null이면 모든 레이블 탐색.
 * @param direction 탐색 방향 (OUTGOING, INCOMING, BOTH)
 * @param maxDepth 최대 탐색 깊이 (기본값: 1)
 */
data class NeighborOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 1,
): GraphTraversalOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = NeighborOptions()
    }
}

/**
 * [GraphTraversalRepository.shortestPath] / [GraphTraversalRepository.allPaths] 호출 옵션.
 *
 * ```kotlin
 * val opts = PathOptions(edgeLabel = "KNOWS", maxDepth = 5)
 * val path = ops.shortestPath(alice.id, carol.id, opts)
 * ```
 *
 * @param edgeLabel 탐색할 엣지 레이블. null이면 모든 레이블 탐색.
 * @param maxDepth 최대 탐색 깊이 (기본값: 10)
 */
data class PathOptions(
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
): GraphTraversalOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PathOptions()
    }
}

/**
 * [GraphTraversalRepository.bfs] / [GraphTraversalRepository.dfs] 호출 옵션.
 *
 * ### 사용 예제
 * ```kotlin
 * val opts = BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3, maxVertices = 1_000)
 * val visits = ops.bfs(alice.id, opts)
 * ```
 */
data class BfsDfsOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 5,
    val maxVertices: Int = 10_000,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxVertices > 0) { "maxVertices must be > 0, was $maxVertices" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = BfsDfsOptions()
    }
}

/**
 * [GraphTraversalRepository.detectCycles] 호출 옵션.
 *
 * @param vertexLabel 탐색할 정점 레이블. null이면 모든 레이블 탐색.
 * @param edgeLabel 탐색할 엣지 레이블. null이면 모든 레이블 탐색.
 * @param maxDepth 최대 탐색 깊이 (기본값: 10)
 * @param maxCycles 반환할 최대 순환 개수 (기본값: 100)
 */
data class CycleOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    val maxCycles: Int = 100,
): GraphTraversalOptions() {
    init {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        require(maxCycles > 0) { "maxCycles must be > 0, was $maxCycles" }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = CycleOptions()
    }
}

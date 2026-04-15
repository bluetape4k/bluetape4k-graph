package io.bluetape4k.graph.model

/**
 * 그래프 간선 탐색 방향.
 *
 * ```kotlin
 * // OUTGOING: alice가 아는 사람들
 * val friends = ops.neighbors(alice.id, NeighborOptions(direction = Direction.OUTGOING, edgeLabel = "KNOWS"))
 *
 * // INCOMING: alice를 아는 사람들
 * val followers = ops.neighbors(alice.id, NeighborOptions(direction = Direction.INCOMING, edgeLabel = "KNOWS"))
 *
 * // BOTH: 양방향
 * val all = ops.neighbors(alice.id, NeighborOptions(direction = Direction.BOTH))
 * ```
 */
enum class Direction {
    /** 출발 정점 -> 도착 정점 방향 */
    OUTGOING,
    /** 도착 정점 -> 출발 정점 방향 */
    INCOMING,
    /** 양방향 */
    BOTH,
}

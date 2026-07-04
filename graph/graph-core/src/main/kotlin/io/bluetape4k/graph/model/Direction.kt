package io.bluetape4k.graph.model

/**
 * Graph edge traversal direction.
 *
 * ```kotlin
 * // OUTGOING: people Alice knows
 * val friends = ops.neighbors(alice.id, NeighborOptions(direction = Direction.OUTGOING, edgeLabel = "KNOWS"))
 *
 * // INCOMING: people who know Alice
 * val followers = ops.neighbors(alice.id, NeighborOptions(direction = Direction.INCOMING, edgeLabel = "KNOWS"))
 *
 * // BOTH: both directions
 * val all = ops.neighbors(alice.id, NeighborOptions(direction = Direction.BOTH))
 * ```
 */
enum class Direction {
    /** From start vertex to end vertex. */
    OUTGOING,
    /** From end vertex to start vertex. */
    INCOMING,
    /** Both directions. */
    BOTH,
}

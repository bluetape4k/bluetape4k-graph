package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import kotlin.math.abs


/**
 * Normalized iterative PageRank calculator used as a JVM fallback.
 *
 * Result scores are normalized to a total near `1.0`. Dangling-node mass from out-degree-zero
 * vertices is redistributed evenly to all vertices on the next iteration.
 *
 * ### Usage
 * ```kotlin
 * val scores = PageRankCalculator.compute(
 *     vertices = vertexIds,
 *     outAdjacency = adjacency,
 *     iterations = 20,
 *     dampingFactor = 0.85,
 *     tolerance = 1e-4,
 * )
 * ```
 */
object PageRankCalculator : KLogging() {

    private const val HASH_LOAD_FACTOR = 0.75

    /**
     * Computes normalized PageRank scores.
	*
     * @param vertices complete vertex ID set.
     * @param outAdjacency out-edge adjacency list. It must not reference vertices outside [vertices].
     * @param iterations maximum iteration count. Must be positive.
     * @param dampingFactor damping factor in the `[0.0, 1.0]` range, typically `0.85`.
     * @param tolerance positive L1-norm threshold for early termination.
     * @return vertex-to-PageRank score map, normalized to a total near `1.0`.
     */
    fun compute(
        vertices: Set<GraphElementId>,
        outAdjacency: Map<GraphElementId, List<GraphElementId>>,
        iterations: Int,
        dampingFactor: Double,
        tolerance: Double,
    ): Map<GraphElementId, Double> {
        require(iterations > 0) { "iterations must be > 0, was $iterations" }
        require(dampingFactor in 0.0..1.0) { "dampingFactor must be in [0, 1], was $dampingFactor" }
        require(tolerance > 0.0) { "tolerance must be > 0.0, was $tolerance" }

        if (vertices.isEmpty()) return emptyMap()

        val n = vertices.size
        // Avoid HashMap rehashing; Double division preserves precision for very large graphs.
        val mapCapacity = ((n / HASH_LOAD_FACTOR) + 1).toInt()
        val initial = 1.0 / n
        var ranks = HashMap<GraphElementId, Double>(mapCapacity)
        vertices.forEach { ranks[it] = initial }

        // The graph structure is fixed, so dangling nodes are computed once outside the loop.
        val danglingNodes = vertices.filter { outAdjacency[it].isNullOrEmpty() }

        repeat(iterations) {
            val newRanks = HashMap<GraphElementId, Double>(mapCapacity)
            // base teleport probability
            val baseRank = (1.0 - dampingFactor) / n

            val danglingMass = danglingNodes.sumOf { ranks.getOrDefault(it, 0.0) }
            val danglingShare = dampingFactor * danglingMass / n

            vertices.forEach { v -> newRanks[v] = baseRank + danglingShare }

            vertices.forEach { src ->
                val outs = outAdjacency[src].orEmpty()
                if (outs.isNotEmpty()) {
                    val share = dampingFactor * ranks.getOrDefault(src, 0.0) / outs.size
                    outs.forEach { dst ->
                        // merge performs read+write with one hash lookup.
                        newRanks.merge(dst, share, Double::plus)
                    }
                }
            }

            val delta = vertices.sumOf { abs((newRanks[it] ?: 0.0) - (ranks[it] ?: 0.0)) }
            ranks = newRanks
            if (delta < tolerance) return ranks
        }

        log.warn { "PageRank did not converge after $iterations iterations (vertices=${vertices.size})" }
        return ranks
    }
}

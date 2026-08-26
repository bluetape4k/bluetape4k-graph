package io.bluetape4k.graph.model

import java.io.InvalidObjectException
import java.io.ObjectInputStream
import java.io.Serializable

private fun validateDeserializedAlgorithmOption(condition: Boolean, message: String) {
    if (!condition) {
        throw InvalidObjectException(message)
    }
}

private fun validateDeserializedAlgorithmOptionNotNull(value: Any?, name: String) {
    validateDeserializedAlgorithmOption(value != null, "$name must not be null")
}

/**
 * Base sealed class for analytics algorithm options.
 *
 * Use this for algorithms without a `maxDepth` concept, such as PageRank, degree centrality,
 * and connected components. Use [GraphTraversalOptions] for algorithms where traversal depth matters.
 *
 * ### Usage
 * ```kotlin
 * val opts: GraphAlgorithmOptions = PageRankOptions(iterations = 20)
 * ```
 *
 * Java deserialization rechecks each concrete option's invariants and throws
 * [InvalidObjectException] for a malformed payload.
 */
sealed class GraphAlgorithmOptions: Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * PageRank options.
 *
 * @param vertexLabel Targets all vertices when `null`.
 * @param edgeLabel Includes all edges when `null`.
 * @param iterations Number of iterations. Defaults to `20`.
 * @param dampingFactor Damping factor. Defaults to `0.85`; backend support varies.
 * @param tolerance Convergence tolerance. Defaults to `1e-4`; must be finite and non-negative.
 * @param topK Returns only the top K results. `Int.MAX_VALUE` returns all results.
 *
 * Result order is guaranteed to be descending by score.
 *
 * ### Usage
 * ```kotlin
 * val opts = PageRankOptions(vertexLabel = "Person", iterations = 30, topK = 10)
 * val top10 = ops.pageRank(opts)
 * ```
 */
data class PageRankOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    val iterations: Int = 20,
    val dampingFactor: Double = 0.85,
    val tolerance: Double = 1e-4,
    val topK: Int = Int.MAX_VALUE,
): GraphAlgorithmOptions() {
    init {
        require(iterations > 0) { "iterations must be > 0, was $iterations" }
        require(topK > 0) { "topK must be > 0, was $topK" }
        require(dampingFactor in 0.0..1.0) { "dampingFactor must be in [0,1], was $dampingFactor" }
        require(tolerance >= 0.0 && tolerance.isFinite()) {
            "tolerance must be finite and >= 0.0, was $tolerance"
        }
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PageRankOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedAlgorithmOption(iterations > 0, "iterations must be > 0, was $iterations")
        validateDeserializedAlgorithmOption(topK > 0, "topK must be > 0, was $topK")
        validateDeserializedAlgorithmOption(
            dampingFactor in 0.0..1.0,
            "dampingFactor must be in [0,1], was $dampingFactor",
        )
        validateDeserializedAlgorithmOption(
            tolerance >= 0.0 && tolerance.isFinite(),
            "tolerance must be finite and >= 0.0, was $tolerance",
        )
    }
}

/**
 * Degree centrality options.
 *
 * @param edgeLabel Includes all edges when `null`.
 * @param direction Traversal direction: `BOTH`, `OUTGOING`, or `INCOMING`.
 * The direction is required to be non-null across Java serialization boundaries.
 *
 * ### Usage
 * ```kotlin
 * val opts = DegreeOptions(edgeLabel = "KNOWS", direction = Direction.BOTH)
 * val degree = ops.degreeCentrality(alice.id, opts)
 * ```
 */
data class DegreeOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.BOTH,
): GraphAlgorithmOptions() {
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = DegreeOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedAlgorithmOptionNotNull(direction, "direction")
    }
}

/**
 * Connected components options.
 *
 * @param vertexLabel Targets all vertices when `null`.
 * @param edgeLabel Includes all edges when `null`.
 * @param weakly `true` for weakly connected components, ignoring direction; `false` for strongly connected components.
 * @param minSize Minimum component size to return. Defaults to `1`; must be positive.
 *
 * ### Usage
 * ```kotlin
 * val opts = ComponentOptions(weakly = true, minSize = 2)
 * val components = ops.connectedComponents(opts)
 * ```
 */
data class ComponentOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    val weakly: Boolean = true,
    val minSize: Int = 1,
): GraphAlgorithmOptions() {
    init {
        require(minSize > 0) { "minSize must be > 0, was $minSize" }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = ComponentOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedAlgorithmOption(minSize > 0, "minSize must be > 0, was $minSize")
    }
}

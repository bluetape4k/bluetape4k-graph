package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Policy for edges that lack a weight property during weighted shortest-path search.
 *
 * Applies when [PathOptions.weightProperty] is set but an edge lacks that property.
 *
 * ```kotlin
 * // Throw on missing weights (default).
 * val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.Fail)
 *
 * // Skip edges with missing weights.
 * val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.Skip)
 *
 * // Use default weight 1.0 for missing weights.
 * val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0))
 * ```
 */
sealed class MissingWeightPolicy : Serializable {

    /**
     * Throws [MissingWeightException] when a weight is missing. This is the default policy.
     *
     * Use this when data integrity matters and every edge must have a weight.
     */
    data object Fail : MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
    }

    /**
     * Excludes edges without the weight property from the path.
     *
     * Useful for sparse graphs where only some edges have weights.
     */
    data object Skip : MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
    }

    /**
     * Applies [value] as the default cost for edges without the weight property.
     *
     * [value] must be finite and `> 0.0`.
     * `0.0` is not allowed because zero-cost edges can expand indefinitely and exceed [PathOptions.maxVisited].
     *
     * ```kotlin
     * MissingWeightPolicy.UseDefault(1.0) // Missing edge cost = 1.0
     * MissingWeightPolicy.UseDefault(0.0) // IllegalArgumentException
     * ```
     *
     * @param value Default cost for missing edge weights. Must be finite and `> 0.0`.
     * @throws IllegalArgumentException if value is non-positive, infinite, or NaN.
     */
    data class UseDefault(val value: Double) : MissingWeightPolicy() {
        companion object {
            private const val serialVersionUID: Long = 1L
        }

        init {
            require(value > 0.0 && value.isFinite()) {
                "default weight must be finite and > 0.0, was $value"
            }
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Exception thrown for an edge without a weight when [MissingWeightPolicy.Fail] is used.
 *
 * Subtype of [IllegalStateException] for log aggregation and monitoring alerts.
 *
 * @param edgeId ID of the edge without a weight.
 * @param key Weight property key that was queried.
 */
class MissingWeightException(
    val edgeId: GraphElementId,
    val key: String,
) : IllegalStateException("edge $edgeId missing weight property '$key'")

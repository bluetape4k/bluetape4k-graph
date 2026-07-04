package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.MissingWeightException
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * Extracts a `Double` weight from edge properties.
 *
 * [MissingWeightPolicy] controls missing property handling:
 * - [MissingWeightPolicy.Fail]: throws [MissingWeightException].
 * - [MissingWeightPolicy.Skip]: returns `null` so the caller can skip the edge.
 * - [MissingWeightPolicy.UseDefault]: returns [MissingWeightPolicy.UseDefault.value].
 *
 * Existing values that are `NaN`, infinite, negative, or zero throw [IllegalArgumentException].
 *
 * ```kotlin
 * val extractor = WeightExtractor("cost", MissingWeightPolicy.UseDefault(1.0))
 * val weight = extractor.extract(edge)  // caller skips when null
 * ```
 */
class WeightExtractor(
    private val weightProperty: String,
    private val policy: MissingWeightPolicy,
) {
    companion object : KLogging()

    /**
     * Extracts the weight from [edge].
	*
     * @return weight value, or `null` when [MissingWeightPolicy.Skip] is active and the property is absent.
     * @throws MissingWeightException when [MissingWeightPolicy.Fail] is active and the property is absent.
     * @throws IllegalArgumentException when the extracted value is invalid (`NaN`, infinity, or <= 0).
     */
    fun extract(edge: GraphEdge): Double? {
        val raw = edge.properties[weightProperty]

        if (raw == null) {
            return when (policy) {
                is MissingWeightPolicy.Fail -> throw MissingWeightException(edge.id, weightProperty)
                is MissingWeightPolicy.Skip -> {
                    log.debug { "Skipping edge ${edge.id}: missing weight property '$weightProperty'" }
                    null
                }
                is MissingWeightPolicy.UseDefault -> policy.value
            }
        }

        return toValidWeight(raw, edge)
    }

    private fun toValidWeight(raw: Any, edge: GraphEdge): Double {
        val value: Double = when (raw) {
            is Double -> raw
            is Float -> raw.toDouble()
            is Int -> raw.toDouble()
            is Long -> raw.toDouble()
            is Short -> raw.toDouble()
            is Byte -> raw.toDouble()
            is java.math.BigDecimal -> {
                require(raw <= java.math.BigDecimal.valueOf(Double.MAX_VALUE)) {
                    "edge ${edge.id} weight overflows Double.MAX_VALUE: $raw"
                }
                val d = raw.toDouble()
                require(d.isFinite()) { "edge ${edge.id} weight overflows Double: $raw" }
                d
            }
            is Number -> {
                val d = raw.toDouble()
                require(d.isFinite()) { "edge ${edge.id} weight overflows Double: $raw" }
                d
            }
            is String -> raw.toDoubleOrNull()
                ?: throw IllegalArgumentException(
                    "edge ${edge.id} weight property '$weightProperty' is not numeric: '$raw'"
                )
            else -> throw IllegalArgumentException(
                "edge ${edge.id} weight property '$weightProperty' has unsupported type ${raw::class.simpleName}"
            )
        }

        require(value.isFinite()) { "edge ${edge.id} weight must be finite, was $value" }
        require(value > 0.0) { "edge ${edge.id} weight must be > 0.0, was $value" }
        return value
    }
}

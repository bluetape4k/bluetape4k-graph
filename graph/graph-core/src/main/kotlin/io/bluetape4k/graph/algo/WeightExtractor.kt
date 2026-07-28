package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.MissingWeightException
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * edge property에서 `Double` weight를 추출한다.
 *
 * [MissingWeightPolicy] controls missing property handling:
 * - [MissingWeightPolicy.Fail]: throws [MissingWeightException].
 * - [MissingWeightPolicy.Skip]: returns `null` so the caller can skip the edge.
 * - [MissingWeightPolicy.UseDefault]: returns [MissingWeightPolicy.UseDefault.value].
 *
 * 기존 value가 `NaN`, infinite, negative, zero이면 [IllegalArgumentException]을 던진다.
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
     * [edge]에서 weight를 추출한다.
	*
     * @return weight value. [MissingWeightPolicy.Skip]이 active이고 property가 없으면 `null`.
     * @throws MissingWeightException [MissingWeightPolicy.Fail]이 active이고 property가 없을 때.
     * @throws IllegalArgumentException 추출한 value가 invalid(`NaN`, infinity, <= 0)일 때.
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

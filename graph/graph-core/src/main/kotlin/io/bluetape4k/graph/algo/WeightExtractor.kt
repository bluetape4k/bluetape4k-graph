package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.MissingWeightException
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug

/**
 * 간선 속성에서 가중치(Double)를 추출한다.
 *
 * [MissingWeightPolicy]에 따라 결측 속성 처리 방식이 결정된다.
 * - [MissingWeightPolicy.Fail]: 결측 시 [MissingWeightException] 발생
 * - [MissingWeightPolicy.Skip]: `null` 반환 (호출자가 간선 건너뜀)
 * - [MissingWeightPolicy.UseDefault]: [MissingWeightPolicy.UseDefault.value] 반환
 *
 * 속성이 존재하더라도 `NaN`, `Infinity`, 음수, 0.0인 경우 [IllegalArgumentException]을 발생시킨다.
 *
 * ```kotlin
 * val extractor = WeightExtractor("cost", MissingWeightPolicy.UseDefault(1.0))
 * val weight = extractor.extract(edge)  // null이면 호출자가 skip
 * ```
 */
class WeightExtractor(
    private val weightProperty: String,
    private val policy: MissingWeightPolicy,
) {
    companion object : KLogging()

    /**
     * [edge]에서 가중치를 추출한다.
     *
     * @return 가중치 값. [MissingWeightPolicy.Skip]이고 속성이 없으면 `null`.
     * @throws MissingWeightException [MissingWeightPolicy.Fail]이고 속성이 없는 경우.
     * @throws IllegalArgumentException 추출된 값이 유효하지 않은 경우 (NaN, Infinity, <= 0).
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

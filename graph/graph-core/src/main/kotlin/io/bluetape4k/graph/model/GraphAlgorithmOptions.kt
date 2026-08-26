package io.bluetape4k.graph.model

import io.bluetape4k.support.requireFinite
import io.bluetape4k.support.requireInRange
import io.bluetape4k.support.requirePositiveNumber
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
 * @param iterations 반복 횟수의 양수 값. 기본값은 `20`이다.
 * @param dampingFactor `[0.0, 1.0]` 범위의 유한 감쇠 계수. 기본값은 `0.85`이며
 * backend 지원 여부는 다를 수 있다.
 * @param tolerance 수렴 판정에 사용하는 양의 유한 허용 오차. 기본값은 `1e-4`이며
 * backend 지원 여부는 다를 수 있다.
 * @param topK 결과 수의 양수 상한. `Int.MAX_VALUE`이면 모든 결과를 반환한다.
 * @throws IllegalArgumentException 반복·결과 상한이 양수가 아니거나, 감쇠 계수가
 * 유한하지 않거나 `[0.0, 1.0]` 범위를 벗어나거나, 허용 오차가 양의 유한값이 아니면 발생한다.
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
        iterations.requirePositiveNumber("iterations")
        topK.requirePositiveNumber("topK")
        dampingFactor.requireFinite("dampingFactor").requireInRange(0.0, 1.0, "dampingFactor")
        tolerance.requireFinite("tolerance").requirePositiveNumber("tolerance")
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
            dampingFactor.isFinite() && dampingFactor in 0.0..1.0,
            "dampingFactor must be finite and in [0,1], was $dampingFactor",
        )
        validateDeserializedAlgorithmOption(
            tolerance > 0.0 && tolerance.isFinite(),
            "tolerance must be finite and > 0.0, was $tolerance",
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
 * @param minSize 반환할 컴포넌트 크기의 양수 하한. 기본값은 `1`이다.
 * @throws IllegalArgumentException [minSize]가 양수가 아니면 발생한다.
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
        minSize.requirePositiveNumber("minSize")
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

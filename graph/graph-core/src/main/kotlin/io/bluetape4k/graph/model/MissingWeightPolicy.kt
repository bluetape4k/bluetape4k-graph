package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 가중치 최단 경로 탐색 시 간선에 weight 속성이 없을 때의 처리 정책.
 *
 * [PathOptions.weightProperty]가 지정되었으나 해당 간선에 속성이 없을 때 적용된다.
 *
 * ```kotlin
 * // 결측 시 예외 (기본)
 * val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.Fail)
 *
 * // 결측 간선 건너뜀
 * val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.Skip)
 *
 * // 결측 시 기본값 1.0 사용
 * val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0))
 * ```
 */
sealed class MissingWeightPolicy : Serializable {

    /**
     * 결측 시 [MissingWeightException]을 던진다 (기본 정책).
     *
     * 데이터 무결성이 중요하고 모든 간선에 weight가 있어야 하는 경우에 사용한다.
     */
    data object Fail : MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
    }

    /**
     * weight 속성이 없는 간선을 경로에서 제외한다.
     *
     * 일부 간선에만 weight가 있는 스파스 그래프에서 유용하다.
     */
    data object Skip : MissingWeightPolicy() {
        private const val serialVersionUID: Long = 1L
    }

    /**
     * weight 속성이 없는 간선에 [value]를 기본 비용으로 적용한다.
     *
     * [value]는 반드시 `> 0.0`이고 유한한 값이어야 한다.
     * `0.0`은 허용하지 않는다 — zero-cost 간선이 있으면 무한 확장 위험이 있어 [PathOptions.maxVisited] 한도를 초과할 수 있다.
     *
     * ```kotlin
     * MissingWeightPolicy.UseDefault(1.0) // 결측 간선 비용 = 1.0
     * MissingWeightPolicy.UseDefault(0.0) // IllegalArgumentException
     * ```
     *
     * @param value 결측 간선에 적용할 기본 비용 (> 0.0, finite).
     * @throws IllegalArgumentException value가 0 이하이거나 무한대/NaN인 경우.
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
 * [MissingWeightPolicy.Fail] 정책 적용 시 weight 속성이 없는 간선에서 발생하는 예외.
 *
 * [IllegalStateException]의 하위 타입으로 로그 집계 및 모니터링 알림 설정에 활용할 수 있다.
 *
 * @param edgeId weight가 없는 간선 ID.
 * @param key 조회한 weight 속성 키.
 */
class MissingWeightException(
    val edgeId: GraphElementId,
    val key: String,
) : IllegalStateException("edge $edgeId missing weight property '$key'")

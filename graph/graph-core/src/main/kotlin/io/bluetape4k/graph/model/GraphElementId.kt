package io.bluetape4k.graph.model

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * 그래프 요소(Vertex, Edge)의 백엔드 독립 ID.
 *
 * 내부적으로 [String]을 감싸는 인라인 값 클래스이며, 다양한 그래프 백엔드의 ID 표현을
 * 단일 타입으로 통합한다.
 *
 * - Apache AGE: `Long` 내부 ID → `GraphElementId("$longId")` 변환
 * - Neo4j: `elementId()` (String) → `GraphElementId` 직접 매핑
 * - TinkerGraph: 객체 ID → `toString()` 변환
 *
 * @property value 실제 ID 문자열 값.
 *
 * ### 사용 예제
 * ```kotlin
 * val id1 = GraphElementId.of("node-abc")
 * val id2 = GraphElementId.of(42L)  // AGE Long ID
 * ```
 */
@JvmInline
value class GraphElementId(val value: String): Serializable {
    init {
        value.requireNotBlank("value")
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        /**
         * 문자열 값으로 [GraphElementId]를 생성한다.
         *
         * ```kotlin
         * val id = GraphElementId.of("node-abc")
         * ```
         *
         * @param value ID 문자열 값.
         */
        fun of(value: String): GraphElementId {
            return GraphElementId(value)
        }

        /**
         * Long 숫자 ID를 [GraphElementId]로 변환한다.
         *
         * Apache AGE처럼 내부 ID가 Long인 백엔드에서 사용한다.
         *
         * ```kotlin
         * val id = GraphElementId.of(42L)  // AGE Long ID → "42"
         * ```
         *
         * @param value Long 형식의 숫자 ID.
         */
        fun of(value: Long): GraphElementId {
            return GraphElementId(value.toString())
        }
    }
}

/**
 * 임의 타입의 값을 [GraphElementId]로 변환한다.
 *
 * - [GraphElementId]를 그대로 전달하면 새 인스턴스를 만들지 않고 반환한다.
 * - [Long]은 [GraphElementId.of] Long 오버로드로 위임한다.
 * - 그 외 타입은 `toString()` 결과를 ID 값으로 사용한다.
 *
 * ```kotlin
 * graphElementIdOf("node-1")           // GraphElementId("node-1")
 * graphElementIdOf(42L)                // GraphElementId("42")
 * graphElementIdOf(GraphElementId("x")) // GraphElementId("x") — 이중 변환 없음
 * ```
 *
 * @param value ID로 변환할 값. `Any` 타입이지만 `toString()` 결과가 비어있어선 안 된다.
 */
fun graphElementIdOf(value: Any): GraphElementId = when (value) {
    is GraphElementId -> value
    is Long -> GraphElementId.of(value)
    else -> GraphElementId.of(value.toString())
}

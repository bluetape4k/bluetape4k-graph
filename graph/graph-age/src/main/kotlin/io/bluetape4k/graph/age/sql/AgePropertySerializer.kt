package io.bluetape4k.graph.age.sql

import io.bluetape4k.graph.utils.GraphProperties

/**
 * Kotlin 값을 Cypher 프로퍼티 문자열로 직렬화한다.
 *
 * 실제 직렬화 로직은 [GraphProperties] 에 있으며, 이 객체는 AGE 전용 별칭이다.
 * 새 코드는 [GraphProperties] 를 직접 사용해도 된다.
 *
 * ```kotlin
 * AgePropertySerializer.toCypherProps(mapOf("name" to "Alice", "age" to 30))
 * // → "{name: 'Alice', age: 30}"
 *
 * AgePropertySerializer.toCypherValue("hello") // → "'hello'"
 * AgePropertySerializer.toCypherValue(42)      // → "42"
 * ```
 */
object AgePropertySerializer {

    /**
     * Map을 Cypher 속성 블록 문자열로 변환한다.
     *
     * @see GraphProperties.toCypherProps
     */
    fun toCypherProps(properties: Map<String, Any?>): String =
        GraphProperties.toCypherProps(properties)

    /**
     * 단일 값을 Cypher 리터럴로 변환한다.
     *
     * @see GraphProperties.toCypherValue
     */
    fun toCypherValue(value: Any?): String =
        GraphProperties.toCypherValue(value)
}

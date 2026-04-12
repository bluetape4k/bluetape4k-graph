package io.bluetape4k.graph.age.sql

import io.bluetape4k.graph.utils.GraphProperties

/**
 * Kotlin 값을 Cypher 프로퍼티 문자열로 직렬화한다.
 *
 * 실제 직렬화 로직은 [GraphProperties] 에 있으며, 이 객체는 AGE 전용 별칭이다.
 * 새 코드는 [GraphProperties] 를 직접 사용해도 된다.
 */
object AgePropertySerializer {

    fun toCypherProps(properties: Map<String, Any?>): String =
        GraphProperties.toCypherProps(properties)

    fun toCypherValue(value: Any?): String =
        GraphProperties.toCypherValue(value)
}

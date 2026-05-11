package io.bluetape4k.graph.age.sql

import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * AGE Cypher 리터럴에 넣을 Kotlin 값을 안전한 프로퍼티 문자열로 직렬화한다.
 *
 * AGE 연산은 `ag_catalog.cypher(...)` SQL 문자열 안에 Cypher를 넣기 때문에, 드라이버 파라미터
 * 바인딩을 쓰는 Neo4j/Memgraph와 달리 리터럴 직렬화의 안전성이 중요하다.
 *
 * ## 동작/계약
 *
 * - 문자열 값은 작은따옴표, 백슬래시, 개행, 탭을 escape 한다.
 * - map key는 Cypher identifier로 직접 들어가므로 [requireSafeIdentifier] 검증을 통과해야 한다.
 * - 중첩 map key도 동일하게 검증한다.
 * - 지원하지 않는 객체는 `toString()` 결과를 문자열 리터럴로 직렬화한다.
 *
 * ```kotlin
 * AgePropertySerializer.toCypherProps(mapOf("name" to "Alice", "age" to 30))
 * // → "{name: 'Alice', age: 30}"
 *
 * AgePropertySerializer.toCypherValue("hello") // → "'hello'"
 * AgePropertySerializer.toCypherValue(42)      // -> "42"
 * ```
 */
object AgePropertySerializer {

    /**
     * Map을 Cypher 속성 블록 문자열로 변환한다.
     *
     * ```kotlin
     * AgePropertySerializer.toCypherProps(mapOf("name" to "Alice", "age" to 30))
     * // → "{name: 'Alice', age: 30}"
     * ```
     *
     * @throws IllegalArgumentException 프로퍼티 키가 안전한 Cypher identifier가 아닐 때.
     */
    fun toCypherProps(properties: Map<String, Any?>): String =
        if (properties.isEmpty()) {
            ""
        } else {
            properties.entries.joinToString(", ", "{", "}") { (key, value) ->
                "${safeKey(key)}: ${toCypherValue(value)}"
            }
        }

    /**
     * `SET v.name = 'Alice', v.age = 30` 형태의 AGE Cypher assignment 절을 생성한다.
     *
     * @param variable Cypher 변수 이름.
     * @param properties 설정할 속성 맵.
     */
    fun toCypherAssignments(variable: String, properties: Map<String, Any?>): String {
        val safeVariable = variable.requireNotBlank("variable").requireSafeIdentifier("variable")
        return properties.entries.joinToString(", ") { (key, value) ->
            "$safeVariable.${safeKey(key)} = ${toCypherValue(value)}"
        }
    }

    /**
     * 단일 값을 Cypher 리터럴로 변환한다.
     *
     * ```kotlin
     * AgePropertySerializer.toCypherValue("Alice")  // → "'Alice'"
     * AgePropertySerializer.toCypherValue(42)       // → "42"
     * ```
     *
     * @throws IllegalArgumentException 중첩 map key가 안전한 Cypher identifier가 아닐 때.
     */
    fun toCypherValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "'${escapeString(value)}'"
        is Number -> value.toString()
        is Boolean -> value.toString()
        is LocalDate -> "'$value'"
        is LocalDateTime -> "'$value'"
        is List<*> -> value.joinToString(", ", "[", "]") { toCypherValue(it) }
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (key, nestedValue) ->
            "${safeNestedKey(key)}: ${toCypherValue(nestedValue)}"
        }
        else -> "'${escapeString(value.toString())}'"
    }

    private fun safeNestedKey(key: Any?): String {
        require(key is String) {
            "nested property key must be a String: $key"
        }
        return safeKey(key)
    }

    private fun safeKey(key: String): String =
        key.requireNotBlank("property key").requireSafeIdentifier("property key")

    private fun escapeString(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}

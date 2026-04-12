package io.bluetape4k.graph.utils

import io.bluetape4k.logging.KLogging
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 그래프 속성(Properties) 변환 유틸리티.
 *
 * Kotlin 값을 Cypher 리터럴로 직렬화한다. Neo4j / Memgraph / Apache AGE 에서
 * 공통으로 사용 가능한 Cypher 서브셋을 생성한다.
 *
 * 지원 타입: `null`, `String`, `Number`, `Boolean`, `LocalDate`, `LocalDateTime`,
 * `List<*>`, `Map<*, *>` (중첩 허용), 그리고 위에 속하지 않는 객체는 `toString()`
 * 후 문자열로 취급된다.
 *
 * > 주의: 이 유틸리티는 **신뢰된 입력**을 대상으로 하는 리터럴 생성기이다.
 * > 사용자 입력을 그대로 Cypher 에 끼워 넣는 용도로 사용해서는 안 된다.
 * > 사용자 입력은 백엔드 드라이버의 파라미터 바인딩을 사용할 것.
 */
object GraphProperties: KLogging() {

    /**
     * Map 을 Cypher 속성 블록 문자열로 변환한다. 예: `{name: 'Alice', age: 30}`.
     *
     * 입력이 비어 있으면 빈 문자열을 반환한다.
     */
    fun toCypherProps(properties: Map<String, Any?>): String {
        if (properties.isEmpty()) return ""
        return properties.entries.joinToString(", ", "{", "}") { (key, value) ->
            "$key: ${toCypherValue(value)}"
        }
    }

    /**
     * 단일 값을 Cypher 리터럴로 변환한다.
     *
     * - 문자열은 작은따옴표로 감싸지며, 백슬래시/작은따옴표/개행/탭은 escape 된다.
     * - `LocalDate`, `LocalDateTime` 은 ISO-8601 문자열 리터럴로 변환된다.
     * - `List`, `Map` 은 요소를 재귀 변환한다.
     */
    fun toCypherValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "'${escapeString(value)}'"
        is Number -> value.toString()
        is Boolean -> value.toString()
        is LocalDate -> "'$value'"
        is LocalDateTime -> "'$value'"
        is List<*> -> value.joinToString(", ", "[", "]") { toCypherValue(it) }
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (k, v) ->
            "$k: ${toCypherValue(v)}"
        }
        else -> "'${escapeString(value.toString())}'"
    }

    /**
     * Cypher 문자열 리터럴 내부의 특수 문자를 escape 한다.
     *
     * 백슬래시는 반드시 먼저 처리해야 이어지는 escape sequence 가 중복되지 않는다.
     */
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

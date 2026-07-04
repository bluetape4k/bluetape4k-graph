package io.bluetape4k.graph.utils

import io.bluetape4k.logging.KLogging
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Graph property conversion utilities.
 *
 * Serializes Kotlin values to Cypher literals using a subset shared by Neo4j, Memgraph, and Apache AGE.
 *
 * Supported values: `null`, `String`, `Number`, `Boolean`, `LocalDate`, `LocalDateTime`,
 * nested `List<*>`, nested `Map<*, *>`, and fallback objects converted with `toString()`.
 *
 * > Warning: this is a literal generator for trusted input. Do not splice raw user input
 * > into Cypher with this utility; use backend driver parameter binding instead.
 *
 * ```kotlin
 * GraphProperties.toCypherProps(mapOf("name" to "Alice", "age" to 30))
 * // → "{name: 'Alice', age: 30}"
 *
 * GraphProperties.toCypherValue("hello")  // → "'hello'"
 * GraphProperties.toCypherValue(42)       // → "42"
 * GraphProperties.toCypherValue(null)     // → "null"
 * ```
 *
 */
object GraphProperties: KLogging() {

    /**
     * Converts a map to a Cypher property block, for example `{name: 'Alice', age: 30}`.
	*
     * Returns an empty string when the input is empty.
     *
     * ```kotlin
     * GraphProperties.toCypherProps(mapOf("name" to "Alice", "age" to 30))
     * // → "{name: 'Alice', age: 30}"
     *
     * GraphProperties.toCypherProps(emptyMap())
     * // → ""
     * ```
     */
    fun toCypherProps(properties: Map<String, Any?>): String {
        if (properties.isEmpty()) return ""
        return properties.entries.joinToString(", ", "{", "}") { (key, value) ->
            "$key: ${toCypherValue(value)}"
        }
    }

    /**
     * Converts a single value to a Cypher literal.
	*
     * - Strings are single-quoted, with backslash, quote, newline, and tab escaped.
     * - `LocalDate` and `LocalDateTime` are converted to ISO-8601 string literals.
     * - `List` and `Map` values are converted recursively.
     *
     * ```kotlin
     * GraphProperties.toCypherValue("Alice")          // → "'Alice'"
     * GraphProperties.toCypherValue(42)               // → "42"
     * GraphProperties.toCypherValue(true)             // → "true"
     * GraphProperties.toCypherValue(null)             // → "null"
     * GraphProperties.toCypherValue(listOf(1, 2, 3)) // → "[1, 2, 3]"
     * ```
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
     * Escapes special characters inside a Cypher string literal.
	*
     * Backslashes must be processed first to avoid double-escaping later sequences.
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

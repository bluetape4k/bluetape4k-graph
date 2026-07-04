package io.bluetape4k.graph.model

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Backend-independent ID for graph elements: vertices and edges.
 *
 * Inline value class that wraps a [String] and unifies ID representations across graph backends.
 *
 * - Apache AGE: converts internal `Long` IDs to `GraphElementId("$longId")`.
 * - Neo4j: maps `elementId()` strings directly to `GraphElementId`.
 * - TinkerGraph: converts object IDs with `toString()`.
 *
 * @property value Actual ID string value.
 *
 * ### Usage
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
         * Creates a [GraphElementId] from a string value.
         *
         * ```kotlin
         * val id = GraphElementId.of("node-abc")
         * ```
         *
         * @param value ID string value.
         */
        fun of(value: String): GraphElementId {
            return GraphElementId(value)
        }

        /**
         * Converts a numeric [Long] ID to [GraphElementId].
         *
         * Use this for backends such as Apache AGE whose internal IDs are [Long] values.
         *
         * ```kotlin
         * val id = GraphElementId.of(42L)  // AGE Long ID -> "42"
         * ```
         *
         * @param value Numeric [Long] ID.
         */
        fun of(value: Long): GraphElementId {
            return GraphElementId(value.toString())
        }
    }
}

/**
 * Converts a value of any type to [GraphElementId].
 *
 * - Returns [GraphElementId] values unchanged.
 * - Delegates [Long] values to the [GraphElementId.of] overload.
 * - Uses `toString()` as the ID value for other types.
 *
 * ```kotlin
 * graphElementIdOf("node-1")           // GraphElementId("node-1")
 * graphElementIdOf(42L)                // GraphElementId("42")
 * graphElementIdOf(GraphElementId("x")) // GraphElementId("x") - no duplicate conversion
 * ```
 *
 * @param value Value to convert to an ID. Its `toString()` result must not be blank.
 */
fun graphElementIdOf(value: Any): GraphElementId = when (value) {
    is GraphElementId -> value
    is Long -> GraphElementId.of(value)
    else -> GraphElementId.of(value.toString())
}

package io.bluetape4k.graph.schema

import io.bluetape4k.support.hashOf

/**
 * Graph edge schema definition.
 *
 * It uses the same DSL style as [VertexLabel]. [from] and [to] declare the edge direction
 * and domain constraints.
 *
 * ```kotlin
 * object WorksAtLabel : EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
 *     val role  = string("role")
 *     val since = localDate("since")
 * }
 * ```
 *
 * @property label edge label name, such as `"KNOWS"` or `"WORKS_AT"`.
 * @property from start vertex label.
 * @property to end vertex label.
 */
abstract class EdgeLabel(
    val label: String,
    val from: VertexLabel,
    val to: VertexLabel,
): PropertyHolder() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        return other is EdgeLabel &&
                label == other.label &&
                from == other.from &&
                to == other.to
    }

    override fun hashCode(): Int = hashOf(label, from, to)

    override fun toString(): String = "EdgeLabel($label, from=$from, to=$to)"
}

package io.bluetape4k.graph.schema

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
) : PropertyHolder()

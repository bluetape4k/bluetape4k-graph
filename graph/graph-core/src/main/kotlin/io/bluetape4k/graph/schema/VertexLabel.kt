package io.bluetape4k.graph.schema

/**
 * Graph vertex schema definition with an Exposed Table-style DSL.
 * It is backend-independent and can be used with AGE, Neo4j, and other graph backends.
 *
 * Subclass it as an `object` per vertex type and keep each DSL call result as a [PropertyDef] property.
 *
 * ```kotlin
 * object PersonLabel : VertexLabel("Person") {
 *     val name = string("name")
 *     val age  = integer("age")
 * }
 * ```
 *
 * @property label vertex label name, such as `"Person"` or `"Company"`.
 */
abstract class VertexLabel(val label: String) : PropertyHolder()

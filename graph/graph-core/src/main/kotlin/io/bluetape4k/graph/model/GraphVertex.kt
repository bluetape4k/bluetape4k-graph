package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Graph vertex or node.
 *
 * Immutable vertex model shared by all graph backends: AGE, Neo4j, Memgraph, and TinkerGraph.
 *
 * @property id Backend-independent vertex ID.
 * @property label Label that describes the vertex type, such as `"Person"` or `"Company"`.
 * @property properties Property map attached to the vertex. Values may contain `null`. When a
 *   containing [GraphPath] is written with Java serialization, every non-null value (including
 *   nested map/collection values) must implement [java.io.Serializable].
 *
 * ### Usage
 * ```kotlin
 * val person = GraphVertex(
 *     id = GraphElementId.of("v-1"),
 *     label = "Person",
 *     properties = mapOf("name" to "Alice", "age" to 30)
 * )
 * val copy = person.copy(properties = mapOf("name" to "Bob"))
 * ```
 */
data class GraphVertex(
    val id: GraphElementId,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Creates a vertex from a [GraphElementId] and label.
 *
 * ```kotlin
 * val v = graphVertexOf(GraphElementId.of("v-1"), "Person", mapOf("name" to "Alice"))
 * ```
 *
 * @param id Vertex ID.
 * @param label Vertex label.
 * @param properties Vertex property map. Defaults to an empty map.
 */
fun graphVertexOf(id: GraphElementId, label: String, properties: Map<String, Any?> = emptyMap()) =
    GraphVertex(id, label, properties)

/**
 * Creates a vertex from an ID value of any type.
 *
 * Uses [graphElementIdOf] for ID conversion, so passing a [GraphElementId] does not convert it again.
 *
 * ```kotlin
 * val v = graphVertexOf("v-1", "Person")
 * val v2 = graphVertexOf(42L, "Item", mapOf("name" to "Foo"))
 * ```
 *
 * @param id Vertex ID. Accepts [GraphElementId], [Long], or any type whose `toString()` result is used.
 * @param label Vertex label.
 * @param properties Vertex property map. Defaults to an empty map.
 */
fun graphVertexOf(id: Any, label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex =
    graphVertexOf(graphElementIdOf(id), label, properties)

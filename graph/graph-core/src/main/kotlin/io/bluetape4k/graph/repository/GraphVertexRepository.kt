package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex

/**
 * Synchronous graph vertex CRUD repository.
 *
 * ```kotlin
 * val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
 * val found  = ops.findVertexById("Person", vertex.id) // non-null
 * val list   = ops.findVerticesByLabel("Person", mapOf("age" to 30))
 * val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 31))
 * val deleted = ops.deleteVertex("Person", vertex.id) // true
 * val count  = ops.countVertices("Person") // 0L (deleted)
 * ```
 */
interface GraphVertexRepository {
    /**
     * Creates and returns a new vertex.
     *
     * ```kotlin
     * val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
     * println(vertex.id)    // "v-1"
     * println(vertex.label) // "Person"
     * ```
     *
     * @param label vertex label.
     * @param properties properties to store on the vertex; defaults to an empty map.
     * @return backend-created [GraphVertex] with its ID populated.
     */
    fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex

    /**
     * Creates multiple vertices with the same label and returns them in input order.
	*
     * ## Contract
	*
     * - Empty input returns `emptyList()` without calling the backend.
     * - The default implementation is a compatibility fallback that calls [createVertex] sequentially.
     * - If the default implementation fails mid-batch, previously created vertices may remain.
     * - Production backends that need performance or all-or-fail semantics should override this method.
     *
     * ```kotlin
     * val vertices = ops.createVertices(
     *     "Person",
     *     listOf(
     *         mapOf("name" to "Alice"),
     *         mapOf("name" to "Bob"),
     *     )
     * )
     * ```
     *
     * @param label vertex label.
     * @param propertiesList property maps to store on each vertex.
     * @return backend-created [GraphVertex] values.
     */
    fun createVertices(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): List<GraphVertex> {
        GraphBatchValidation.validateVertexBatch(label, propertiesList)
        if (propertiesList.isEmpty()) return emptyList()
        return propertiesList.map { properties -> createVertex(label, properties) }
    }

    /**
     * Finds one vertex by label and ID.
     *
     * ```kotlin
     * val found = ops.findVertexById("Person", vertex.id)  // non-null
     * val none  = ops.findVertexById("Person", GraphElementId.of("unknown"))  // null
     * ```
     *
     * @param label vertex label.
     * @param id vertex ID to query.
     * @return [GraphVertex] when found, otherwise `null`.
     */
    fun findVertexById(label: String, id: GraphElementId): GraphVertex?

    /**
     * Finds one vertex by ID without a label.
	*
     * Use this when the backend can query vertices by ID alone. Dijkstra/A* algorithms need it
     * when only the vertex ID is known.
     *
     * ```kotlin
     * val found = ops.findVertexById(vertex.id)  // label not required
     * ```
     *
     * @param id vertex ID to query.
     * @return [GraphVertex] when found, otherwise `null`.
     */
    fun findVertexById(id: GraphElementId): GraphVertex?

    /**
     * Finds vertices by label and property filter.
     *
     * ```kotlin
     * val all   = ops.findVerticesByLabel("Person")
     * val aged  = ops.findVerticesByLabel("Person", mapOf("age" to 30))
     * ```
     *
     * @param label vertex label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @return matching [GraphVertex] values.
     */
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphVertex>

    /**
     * Finds vertices by label and property filter as bounded chunks.
     *
     * ## Contract
     *
     * - The default implementation splits the [findVerticesByLabel] result into chunks for compatibility.
     * - Backends that must avoid whole-label materialization during large exports should override this method.
     * - [chunkSize] must be positive.
     *
     * ```kotlin
     * for (chunk in ops.findVerticesByLabelChunked("Person", chunkSize = 500)) {
     *     chunk.forEach { vertex -> println(vertex.id) }
     * }
     * ```
     *
     * @param label vertex label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @param chunkSize maximum number of vertices per chunk.
     * @return chunk sequence containing matching [GraphVertex] values.
     */
    fun findVerticesByLabelChunked(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): Sequence<List<GraphVertex>> =
        findVerticesByLabel(label, filter).asGraphExportChunks(chunkSize)

    /**
     * Updates an existing vertex and returns the updated vertex.
     *
     * ```kotlin
     * val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 31))
     * ```
     *
     * @param label vertex label.
     * @param id vertex ID to update.
     * @param properties new property map, replacing existing properties.
     * @return updated [GraphVertex], or `null` when the ID is absent.
     */
    fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?

    /**
     * Deletes a vertex.
     *
     * ```kotlin
     * val deleted = ops.deleteVertex("Person", vertex.id)  // true
     * ```
     *
     * @param label vertex label.
     * @param id vertex ID to delete.
     * @return `true` when deleted, or `false` when the ID is absent.
     */
    fun deleteVertex(label: String, id: GraphElementId): Boolean

    /**
     * Counts vertices by label.
     *
     * ```kotlin
     * val count = ops.countVertices("Person")  // 1L
     * ```
     *
     * @param label vertex label to count.
     * @return total vertex count for the label.
     */
    fun countVertices(label: String): Long
}

package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow

/**
 * Coroutine graph vertex CRUD repository.
 *
 * Collection-returning operations expose [Flow] so large result sets can stream.
 *
 * ```kotlin
 * runBlocking {
 *     val vertex = ops.createVertex("Person", mapOf("name" to "Alice"))
 *     val found  = ops.findVertexById("Person", vertex.id)
 *     val list   = ops.findVerticesByLabel("Person").toList()
 *     val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 31))
 *     ops.deleteVertex("Person", vertex.id)
 * }
 * ```
 *
 * @see GraphVertexRepository synchronous blocking variant
 */
interface GraphSuspendVertexRepository {
    /**
     * Creates and returns a new vertex.
     *
     * ```kotlin
     * val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
     * ```
     *
     * @param label vertex label.
     * @param properties properties to store on the vertex; defaults to an empty map.
     * @return backend-created [GraphVertex] with its ID populated.
     */
    suspend fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex

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
     * @param label vertex label.
     * @param propertiesList property maps to store on each vertex.
     * @return backend-created [GraphVertex] values.
     */
    suspend fun createVertices(
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
     * ```
     *
     * @param label vertex label.
     * @param id vertex ID to query.
     * @return [GraphVertex] when found, otherwise `null`.
     */
    suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex?

    /**
     * Finds one vertex by ID without a label.
     *
     * ```kotlin
     * val found = ops.findVertexById(vertex.id)
     * ```
     *
     * @param id vertex ID to query.
     * @return [GraphVertex] when found, otherwise `null`.
     */
    suspend fun findVertexById(id: GraphElementId): GraphVertex?

    /**
     * Finds vertices by label and property filter as a stream.
     *
     * This [Flow]-based query is suitable for large result sets.
     *
     * ```kotlin
     * val all  = ops.findVerticesByLabel("Person").toList()
     * val aged = ops.findVerticesByLabel("Person", mapOf("age" to 30)).toList()
     * ```
     *
     * @param label vertex label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @return [Flow] of matching [GraphVertex] values.
     */
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphVertex>

    /**
     * Finds vertices by label and property filter as a Flow of API chunks.
     *
     * The default implementation groups the record Flow from [findVerticesByLabel] and
     * does not promise bounded source execution. Backends with driver cursors or paging
     * APIs can override this method and advertise the bounded chunk capabilities.
     *
     * ```kotlin
     * ops.findVerticesByLabelChunked("Person", chunkSize = 500)
     *     .collect { chunk -> println(chunk.size) }
     * ```
     *
     * @param label vertex label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @param chunkSize maximum number of vertices per chunk.
     * @return Flow of chunks containing matching [GraphVertex] values.
     */
    fun findVerticesByLabelChunked(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): Flow<List<GraphVertex>> =
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
    suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?

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
    suspend fun deleteVertex(label: String, id: GraphElementId): Boolean

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
    suspend fun countVertices(label: String): Long
}

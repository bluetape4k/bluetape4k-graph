package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId

/**
 * Synchronous graph edge CRUD repository.
 *
 * ```kotlin
 * val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 * val edges = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2024))
 * val deleted = ops.deleteEdge("KNOWS", edge.id) // true
 * ```
 */
interface GraphEdgeRepository {
    /**
     * Creates and returns a new edge between two vertices.
     *
     * ```kotlin
     * val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
     * ```
     *
     * @param fromId start vertex ID.
     * @param toId end vertex ID.
     * @param label edge label, such as `"KNOWS"` or `"WORKS_AT"`.
     * @param properties properties to store on the edge; defaults to an empty map.
     * @return backend-created [GraphEdge] with its ID populated.
     */
    fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): GraphEdge

    /**
     * Creates multiple edges with the same label and returns them in input order.
	*
     * ## Contract
	*
     * - Empty input returns `emptyList()` without calling the backend.
     * - The default implementation is a compatibility fallback that calls [createEdge] sequentially.
     * - If the default implementation fails mid-batch, previously created edges may remain.
     * - Production backends that need performance or all-or-fail semantics should override this method.
     *
     * ```kotlin
     * val edges = ops.createEdges(
     *     "KNOWS",
     *     listOf(BatchEdge(alice.id, bob.id, mapOf("since" to 2024)))
     * )
     * ```
     *
     * @param label edge label.
     * @param edges edge endpoints and property rows.
     * @return backend-created [GraphEdge] values.
     */
    fun createEdges(
        label: String,
        edges: List<BatchEdge>,
    ): List<GraphEdge> {
        GraphBatchValidation.validateEdgeBatch(label, edges)
        if (edges.isEmpty()) return emptyList()
        return edges.map { edge -> createEdge(edge.fromId, edge.toId, label, edge.properties) }
    }

    /**
     * Finds edges by label and property filter.
     *
     * ```kotlin
     * val all    = ops.findEdgesByLabel("KNOWS")
     * val recent = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2024))
     * ```
     *
     * @param label edge label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @return matching [GraphEdge] values.
     */
    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphEdge>

    /**
     * Finds edges by label and property filter as API chunks.
     *
     * ## Contract
     *
     * - The default implementation splits the [findEdgesByLabel] result into chunks for compatibility;
     *   this does not promise bounded source execution.
     * - Backends that avoid whole-label materialization during large exports should override this method and
     *   advertise [GraphCapability.BOUNDED_CHUNKED_READ] and [GraphCapability.BOUNDED_CHUNKED_EXPORT].
     * - [chunkSize] must be positive.
     *
     * ```kotlin
     * for (chunk in ops.findEdgesByLabelChunked("KNOWS", chunkSize = 500)) {
     *     chunk.forEach { edge -> println(edge.id) }
     * }
     * ```
     *
     * @param label edge label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @param chunkSize maximum number of edges per chunk.
     * @return chunk sequence containing matching [GraphEdge] values.
     */
    fun findEdgesByLabelChunked(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): Sequence<List<GraphEdge>> =
        findEdgesByLabel(label, filter).asGraphExportChunks(chunkSize)

    /**
     * Finds edges that start at a specific vertex.
	*
     * Used to collect adjacent edges for Dijkstra/A* algorithms.
     *
     * ```kotlin
     * val outEdges = ops.findEdgesByStartId(alice.id)
     * val typed    = ops.findEdgesByStartId(alice.id, edgeLabel = "KNOWS")
     * ```
     *
     * @param startId start vertex ID.
     * @param edgeLabel optional edge-label filter; `null` returns all labels.
     * @return [GraphEdge] values that start at the vertex.
     */
    fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String? = null): List<GraphEdge>

    /**
     * Finds edges that end at a specific vertex.
	*
     * Used for `Direction.INCOMING` and `Direction.BOTH` traversal.
     *
     * ```kotlin
     * val inEdges = ops.findEdgesByEndId(alice.id)
     * ```
     *
     * @param endId end vertex ID.
     * @param edgeLabel optional edge-label filter; `null` returns all labels.
     * @return [GraphEdge] values that end at the vertex.
     */
    fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String? = null): List<GraphEdge>

    /**
     * Deletes an edge.
     *
     * ```kotlin
     * val deleted = ops.deleteEdge("KNOWS", edge.id)  // true
     * ```
     *
     * @param label edge label.
     * @param id edge ID to delete.
     * @return `true` when deleted, or `false` when the ID is absent.
     */
    fun deleteEdge(label: String, id: GraphElementId): Boolean
}

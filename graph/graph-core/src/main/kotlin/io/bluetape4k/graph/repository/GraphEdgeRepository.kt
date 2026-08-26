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
     * label과 property filter로 간선을 API chunk 단위로 조회한다.
     *
     * ## 계약
     *
     * - 기본 구현은 호환성을 위해 [findEdgesByLabel] 결과를 chunk로 나누며 source 실행의 bounded 보장은 제공하지 않는다.
     * - 대규모 export에서 전체 label materialization을 피하는 backend는 이 method를 override하고
     *   [GraphCapability.BOUNDED_CHUNKED_READ]와 [GraphCapability.BOUNDED_CHUNKED_EXPORT]를 광고해야 한다.
     * - [chunkSize]는 양수여야 한다.
     *
     * ```kotlin
     * for (chunk in ops.findEdgesByLabelChunked("KNOWS", chunkSize = 500)) {
     *     chunk.forEach { edge -> println(edge.id) }
     * }
     * ```
     *
     * @param label 조회할 edge label.
     * @param filter property name과 value 조건. 빈 map은 해당 label 전체를 반환한다.
     * @param chunkSize chunk 하나에 포함할 edge 최대 개수.
     * @return 일치하는 [GraphEdge]를 담은 chunk sequence.
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

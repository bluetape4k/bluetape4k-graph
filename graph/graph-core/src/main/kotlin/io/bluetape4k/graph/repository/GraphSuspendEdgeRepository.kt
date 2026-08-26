package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import kotlinx.coroutines.flow.Flow

/**
 * Coroutine graph edge CRUD repository.
 *
 * Collection-returning operations expose [Flow] so large result sets can stream.
 *
 * ```kotlin
 * runBlocking {
 *     val edge    = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *     val edges   = ops.findEdgesByLabel("KNOWS").toList()
 *     val deleted = ops.deleteEdge("KNOWS", edge.id)  // true
 * }
 * ```
 *
 * @see GraphEdgeRepository synchronous blocking variant
 */
interface GraphSuspendEdgeRepository {
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
    suspend fun createEdge(
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
     * @param label edge label.
     * @param edges edge endpoints and property rows.
     * @return backend-created [GraphEdge] values.
     */
    suspend fun createEdges(
        label: String,
        edges: List<BatchEdge>,
    ): List<GraphEdge> {
        GraphBatchValidation.validateEdgeBatch(label, edges)
        if (edges.isEmpty()) return emptyList()
        return edges.map { edge -> createEdge(edge.fromId, edge.toId, label, edge.properties) }
    }

    /**
     * Finds edges by label and property filter as a stream.
     *
     * This [Flow]-based query is suitable for large result sets.
     *
     * ```kotlin
     * val edges = ops.findEdgesByLabel("KNOWS").toList()
     * val filtered = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2024)).toList()
     * ```
     *
     * @param label edge label to query.
     * @param filter property-name to value conditions. An empty map returns the full label.
     * @return [Flow] of matching [GraphEdge] values.
     */
    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphEdge>

    /**
     * label과 property filter로 간선을 API chunk Flow로 조회한다.
     *
     * 기본 구현은 [findEdgesByLabel]의 record Flow를 묶으며 source 실행의 bounded
     * 보장을 제공하지 않는다. driver cursor 또는 paging API를 사용하는 backend는 이
     * method를 override하고 bounded chunk capability를 광고할 수 있다.
     *
     * ```kotlin
     * ops.findEdgesByLabelChunked("KNOWS", chunkSize = 500)
     *     .collect { chunk -> println(chunk.size) }
     * ```
     *
     * @param label 조회할 edge label.
     * @param filter property name과 value 조건. 빈 map은 해당 label 전체를 반환한다.
     * @param chunkSize chunk 하나에 포함할 edge 최대 개수.
     * @return 일치하는 [GraphEdge]를 담은 chunk Flow.
     */
    fun findEdgesByLabelChunked(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): Flow<List<GraphEdge>> =
        findEdgesByLabel(label, filter).asGraphExportChunks(chunkSize)

    /**
     * Finds edges that start at a specific vertex as a stream.
     *
     * ```kotlin
     * val outEdges = ops.findEdgesByStartId(alice.id).toList()
     * ```
     *
     * @param startId start vertex ID.
     * @param edgeLabel optional edge-label filter; `null` returns all labels.
     * @return [Flow] of [GraphEdge] values that start at the vertex.
     */
    fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String? = null): Flow<GraphEdge>

    /**
     * Finds edges that end at a specific vertex as a stream.
     *
     * ```kotlin
     * val inEdges = ops.findEdgesByEndId(alice.id).toList()
     * ```
     *
     * @param endId end vertex ID.
     * @param edgeLabel optional edge-label filter; `null` returns all labels.
     * @return [Flow] of [GraphEdge] values that end at the vertex.
     */
    fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String? = null): Flow<GraphEdge>

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
    suspend fun deleteEdge(label: String, id: GraphElementId): Boolean
}

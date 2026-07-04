package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import java.util.concurrent.CompletableFuture

/**
 * Virtual-thread graph edge CRUD repository.
 *
 * Every method runs the synchronous [GraphEdgeRepository] on a virtual thread and
 * returns the result as `CompletableFuture<T>` for Java interop.
 */
interface GraphVirtualThreadEdgeRepository {

    fun createEdgeAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<GraphEdge>

    fun createEdgesAsync(
        label: String,
        edges: List<BatchEdge>,
    ): CompletableFuture<List<GraphEdge>>

    fun findEdgesByLabelAsync(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<List<GraphEdge>>

    fun findEdgesByStartIdAsync(
        startId: GraphElementId,
        edgeLabel: String? = null,
    ): CompletableFuture<List<GraphEdge>>

    fun findEdgesByEndIdAsync(
        endId: GraphElementId,
        edgeLabel: String? = null,
    ): CompletableFuture<List<GraphEdge>>

    fun deleteEdgeAsync(label: String, id: GraphElementId): CompletableFuture<Boolean>
}

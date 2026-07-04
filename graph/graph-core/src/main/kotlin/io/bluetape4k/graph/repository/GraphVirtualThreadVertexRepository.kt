package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import java.util.concurrent.CompletableFuture

/**
 * Virtual-thread graph vertex CRUD repository.
 *
 * Every method runs the synchronous [GraphVertexRepository] on a virtual thread and
 * returns the result as `CompletableFuture<T>` for Java interop and CompletableFuture pipelines.
 */
interface GraphVirtualThreadVertexRepository {

    fun createVertexAsync(
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<GraphVertex>

    fun createVerticesAsync(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): CompletableFuture<List<GraphVertex>>

    fun findVertexByIdAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<GraphVertex?>

    fun findVertexByIdAsync(id: GraphElementId): CompletableFuture<GraphVertex?>

    fun findVerticesByLabelAsync(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<List<GraphVertex>>

    fun updateVertexAsync(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex?>

    fun deleteVertexAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<Boolean>

    fun countVerticesAsync(label: String): CompletableFuture<Long>
}

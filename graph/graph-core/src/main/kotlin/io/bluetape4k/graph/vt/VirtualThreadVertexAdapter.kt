package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphVertexRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * Adapter that runs all [GraphVertexRepository] methods on virtual threads.
 *
 * Single operations use `virtualFutureOf { }`.
 *
 * @param delegate synchronous [GraphVertexRepository] to delegate to.
 */
class VirtualThreadVertexAdapter(
    private val delegate: GraphVertexRepository,
) : GraphVirtualThreadVertexRepository {

    companion object : KLogging()

    override fun createVertexAsync(
        label: String,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex> =
        virtualFutureOf { delegate.createVertex(label, properties) }

    override fun createVerticesAsync(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): CompletableFuture<List<GraphVertex>> =
        virtualFutureOf { delegate.createVertices(label, propertiesList) }

    override fun findVertexByIdAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<GraphVertex?> =
        virtualFutureOfNullable { delegate.findVertexById(label, id) }

    override fun findVertexByIdAsync(id: GraphElementId): CompletableFuture<GraphVertex?> =
        virtualFutureOfNullable { delegate.findVertexById(id) }

    override fun findVerticesByLabelAsync(
        label: String,
        filter: Map<String, Any?>,
    ): CompletableFuture<List<GraphVertex>> =
        virtualFutureOf { delegate.findVerticesByLabel(label, filter) }

    override fun updateVertexAsync(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex?> =
        virtualFutureOfNullable { delegate.updateVertex(label, id, properties) }

    override fun deleteVertexAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.deleteVertex(label, id) }

    override fun countVerticesAsync(label: String): CompletableFuture<Long> =
        virtualFutureOf { delegate.countVertices(label) }
}

/**
 * Wraps [GraphVertexRepository] in a virtual-thread vertex adapter.
 */
fun GraphVertexRepository.asVirtualThreadVertexRepository(): GraphVirtualThreadVertexRepository =
    VirtualThreadVertexAdapter(this)

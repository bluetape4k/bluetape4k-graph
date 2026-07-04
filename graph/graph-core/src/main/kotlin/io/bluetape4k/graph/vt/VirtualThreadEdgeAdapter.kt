package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphEdgeRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * Adapter that runs all [GraphEdgeRepository] methods on virtual threads.
 *
 * Single operations use `virtualFutureOf { }`.
 *
 * @param delegate synchronous [GraphEdgeRepository] to delegate to.
 */
class VirtualThreadEdgeAdapter(
    private val delegate: GraphEdgeRepository,
) : GraphVirtualThreadEdgeRepository {

    companion object : KLogging()

    override fun createEdgeAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphEdge> =
        virtualFutureOf { delegate.createEdge(fromId, toId, label, properties) }

    override fun createEdgesAsync(
        label: String,
        edges: List<BatchEdge>,
    ): CompletableFuture<List<GraphEdge>> =
        virtualFutureOf { delegate.createEdges(label, edges) }

    override fun findEdgesByLabelAsync(
        label: String,
        filter: Map<String, Any?>,
    ): CompletableFuture<List<GraphEdge>> =
        virtualFutureOf { delegate.findEdgesByLabel(label, filter) }

    override fun findEdgesByStartIdAsync(
        startId: GraphElementId,
        edgeLabel: String?,
    ): CompletableFuture<List<GraphEdge>> =
        virtualFutureOf { delegate.findEdgesByStartId(startId, edgeLabel) }

    override fun findEdgesByEndIdAsync(
        endId: GraphElementId,
        edgeLabel: String?,
    ): CompletableFuture<List<GraphEdge>> =
        virtualFutureOf { delegate.findEdgesByEndId(endId, edgeLabel) }

    override fun deleteEdgeAsync(label: String, id: GraphElementId): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.deleteEdge(label, id) }
}

/**
 * Wraps [GraphEdgeRepository] in a virtual-thread adapter.
 */
fun GraphEdgeRepository.asVirtualThreadEdge(): GraphVirtualThreadEdgeRepository =
    VirtualThreadEdgeAdapter(this)

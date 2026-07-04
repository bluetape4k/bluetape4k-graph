package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphTraversalRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * Adapter that runs all [GraphTraversalRepository] methods on virtual threads.
 *
 * Single operations use `virtualFutureOf { }`.
 *
 * @param delegate synchronous [GraphTraversalRepository] to delegate to.
 */
class VirtualThreadTraversalAdapter(
    private val delegate: GraphTraversalRepository,
) : GraphVirtualThreadTraversalRepository {

    companion object : KLogging()

    override fun neighborsAsync(
        startId: GraphElementId,
        options: NeighborOptions,
    ): CompletableFuture<List<GraphVertex>> =
        virtualFutureOf { delegate.neighbors(startId, options) }

    override fun shortestPathAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): CompletableFuture<GraphPath?> =
        virtualFutureOfNullable { delegate.shortestPath(fromId, toId, options) }

    override fun allPathsAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): CompletableFuture<List<GraphPath>> =
        virtualFutureOf { delegate.allPaths(fromId, toId, options) }

    override fun aStarPathAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): CompletableFuture<GraphPath?> =
        virtualFutureOfNullable { delegate.aStarPath(fromId, toId, options, heuristic) }
}

/**
 * Wraps [GraphTraversalRepository] in a virtual-thread traversal adapter.
 */
fun GraphTraversalRepository.asVirtualThreadTraversal(): GraphVirtualThreadTraversalRepository =
    VirtualThreadTraversalAdapter(this)

package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.repository.GraphSession
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * Adapter that runs [GraphSession] lifecycle operations on virtual threads.
 *
 * All operations use `virtualFutureOf { }`.
 *
 * @param delegate synchronous [GraphSession] to delegate to.
 */
class VirtualThreadSessionAdapter(
    private val delegate: GraphSession,
) : GraphVirtualThreadSession {

    companion object : KLogging()

    override fun createGraphAsync(name: String): CompletableFuture<Unit> =
        virtualFutureOf { delegate.createGraph(name) }

    override fun dropGraphAsync(name: String): CompletableFuture<Unit> =
        virtualFutureOf { delegate.dropGraph(name) }

    override fun graphExistsAsync(name: String): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.graphExists(name) }
}

/**
 * Wraps [GraphSession] in a virtual-thread session adapter.
 */
fun GraphSession.asVirtualThreadSession(): GraphVirtualThreadSession =
    VirtualThreadSessionAdapter(this)

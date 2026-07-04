package io.bluetape4k.graph.vt

import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
import io.bluetape4k.logging.KLogging

/**
 * Unified adapter that exposes all [GraphOperations] capabilities on virtual threads.
 *
 * It composes five focused adapters through Kotlin `by` delegation.
 *
 * ### Usage
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = VirtualThreadOperationsAdapter(ops)
 * val vertex = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
 * val edge = vtOps.createEdgeAsync(from.id, to.id, "KNOWS").join()
 * val scores = vtOps.pageRankAsync().join()
 * ```
 *
 * @param delegate synchronous [GraphOperations] to delegate to.
 */
class VirtualThreadOperationsAdapter(
    private val delegate: GraphOperations,
): GraphVirtualThreadOperations,
   GraphVirtualThreadSession by VirtualThreadSessionAdapter(delegate),
   GraphVirtualThreadVertexRepository by VirtualThreadVertexAdapter(delegate),
   GraphVirtualThreadEdgeRepository by VirtualThreadEdgeAdapter(delegate),
   GraphVirtualThreadTraversalRepository by VirtualThreadTraversalAdapter(delegate),
   GraphVirtualThreadAlgorithmRepository by VirtualThreadAlgorithmAdapter(delegate) {

    companion object: KLogging()

    override fun close() {
        // The delegate is externally owned; callers manage its lifecycle.
    }
}

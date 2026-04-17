package io.bluetape4k.graph.vt

import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSession
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
import io.bluetape4k.logging.KLogging

/**
 * [GraphOperations] 의 모든 기능을 Virtual Thread 위에서 제공하는 통합 어댑터.
 *
 * Kotlin `by` 위임으로 5개 어댑터를 합성한다.
 * executor 없음 — `StructuredTaskScopes.all` 이 Virtual Thread를 직접 관리한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = VirtualThreadOperationsAdapter(ops)
 * val vertex = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
 * val edge = vtOps.createEdgeAsync(from.id, to.id, "KNOWS").join()
 * val scores = vtOps.pageRankAsync().join()
 * ```
 *
 * @param delegate 위임할 동기 [GraphOperations].
 */
class VirtualThreadOperationsAdapter(
    private val delegate: GraphOperations,
): GraphVirtualThreadOperations,
   GraphSession by delegate,
   GraphVirtualThreadSession by VirtualThreadSessionAdapter(delegate),
   GraphVirtualThreadVertexRepository by VirtualThreadVertexAdapter(delegate),
   GraphVirtualThreadEdgeRepository by VirtualThreadEdgeAdapter(delegate),
   GraphVirtualThreadTraversalRepository by VirtualThreadTraversalAdapter(delegate),
   GraphVirtualThreadAlgorithmRepository by VirtualThreadAlgorithmAdapter(delegate) {

    companion object: KLogging()

    override fun close() {
        // StructuredTaskScopes.all 기반 — executor 없으므로 소유권 원칙상 delegate 도 닫지 않는다.
    }
}

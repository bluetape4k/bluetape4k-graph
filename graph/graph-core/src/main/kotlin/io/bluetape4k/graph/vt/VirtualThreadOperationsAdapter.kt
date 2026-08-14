package io.bluetape4k.graph.vt

import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadCapabilitiesOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
import io.bluetape4k.graph.repository.capabilities
import io.bluetape4k.logging.KLogging

/**
 * [GraphOperations]의 CRUD, traversal, algorithm surface를 Virtual Thread에서 실행하는
 * 통합 adapter다.
 *
 * Kotlin `by` delegation으로 다섯 개의 focused adapter를 조합한다. 선택 capability는
 * [capabilities]로 외부 delegate의 매핑을 보존하지만, 아직 `MERGE`, `SCHEMA`,
 * `TRANSACTION`, `CHUNKED_READ`용 `*Async` method를 제공한다는 뜻은 아니다.
 *
 * ### 사용 예
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = VirtualThreadOperationsAdapter(ops)
 * val vertex = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
 * val edge = vtOps.createEdgeAsync(from.id, to.id, "KNOWS").join()
 * val scores = vtOps.pageRankAsync().join()
 * ```
 *
 * @param delegate 호출자가 소유한 synchronous [GraphOperations] delegate.
 */
class VirtualThreadOperationsAdapter(
    private val delegate: GraphOperations,
): GraphVirtualThreadOperations,
   GraphVirtualThreadCapabilitiesOperations,
   GraphVirtualThreadSession by VirtualThreadSessionAdapter(delegate),
   GraphVirtualThreadVertexRepository by VirtualThreadVertexAdapter(delegate),
   GraphVirtualThreadEdgeRepository by VirtualThreadEdgeAdapter(delegate),
   GraphVirtualThreadTraversalRepository by VirtualThreadTraversalAdapter(delegate),
   GraphVirtualThreadAlgorithmRepository by VirtualThreadAlgorithmAdapter(delegate) {

    companion object: KLogging()

    override fun capabilities() = delegate.capabilities()

    /**
     * Borrowed delegate를 조기 종료하지 않도록 facade만 닫는다.
     *
     * delegate의 `close()`는 이 adapter의 호출자가 별도로 수행해야 한다.
     */
    override fun close() {
        // delegate의 lifecycle은 호출자가 소유한다.
    }
}

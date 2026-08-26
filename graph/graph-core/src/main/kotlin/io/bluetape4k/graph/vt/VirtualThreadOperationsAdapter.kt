package io.bluetape4k.graph.vt

import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.algo.provider.GraphAlgorithmExecution
import io.bluetape4k.graph.algo.provider.GraphAlgorithmExecutionObservable
import io.bluetape4k.graph.repository.GraphBoundedChunkOperations
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphCapabilities
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadCapabilitiesOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadChunkedOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadMergeOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadSchemaManagementOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadTransactionalOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.repository.capabilities
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.logging.KLogging

/**
 * [GraphOperations]의 CRUD, traversal, algorithm surface를 Virtual Thread에서 실행하는
 * 통합 adapter다.
 *
 * Kotlin `by` delegation으로 기존 CRUD/traversal/algorithm adapter와 optional
 * merge/schema/transaction/chunked adapter를 조합한다. delegate가 해당 동기
 * capability를 구현한 경우에만 optional capability를 surface에 보고하고, 그렇지
 * 않으면 해당 future가 명시적인 [UnsupportedOperationException]으로 완료된다.
 * `delegateCapabilities()`는 감싼 동기 delegate의 전체 매핑을 별도로 보존한다.
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
   GraphAlgorithmExecutionObservable,
   GraphVirtualThreadSession by VirtualThreadSessionAdapter(delegate),
   GraphVirtualThreadVertexRepository by VirtualThreadVertexAdapter(delegate),
   GraphVirtualThreadEdgeRepository by VirtualThreadEdgeAdapter(delegate),
   GraphVirtualThreadTraversalRepository by VirtualThreadTraversalAdapter(delegate),
   GraphVirtualThreadAlgorithmRepository by VirtualThreadAlgorithmAdapter(delegate),
   GraphVirtualThreadMergeOperations by VirtualThreadMergeAdapter(delegate as? GraphMergeOperations),
   GraphVirtualThreadSchemaManagementOperations by VirtualThreadSchemaAdapter(
       (delegate as? GraphSchemaManagementOperations)?.schemaManager(),
   ),
   GraphVirtualThreadTransactionalOperations by VirtualThreadTransactionalAdapter(
       delegate as? GraphTransactionalOperations,
   ),
   GraphVirtualThreadChunkedOperations by VirtualThreadChunkedAdapter(delegate) {

    companion object: KLogging()

    override val lastAlgorithmExecution: GraphAlgorithmExecution?
        get() = (delegate as? GraphAlgorithmExecutionObservable)?.lastAlgorithmExecution

    override fun delegateCapabilities() = delegate.capabilities()

    override fun surfaceCapabilities(): GraphCapabilities {
        val optional = buildList {
            if (delegate is GraphMergeOperations) add(GraphCapability.MERGE)
            if (delegate is GraphSchemaManagementOperations) add(GraphCapability.SCHEMA)
            if (delegate is GraphTransactionalOperations) add(GraphCapability.TRANSACTION)
            add(GraphCapability.CHUNKED_READ)
            add(GraphCapability.CHUNKED_EXPORT)
            if (delegate is GraphBoundedChunkOperations) {
                add(GraphCapability.BOUNDED_CHUNKED_READ)
                add(GraphCapability.BOUNDED_CHUNKED_EXPORT)
            }
        }
        return GraphCapabilities.from(this).withAdditional(*optional.toTypedArray())
    }

    /**
     * Borrowed delegate를 조기 종료하지 않도록 facade만 닫는다.
     *
     * delegate의 `close()`는 이 adapter의 호출자가 별도로 수행해야 한다.
     */
    override fun close() {
        // delegate의 lifecycle은 호출자가 소유한다.
    }
}

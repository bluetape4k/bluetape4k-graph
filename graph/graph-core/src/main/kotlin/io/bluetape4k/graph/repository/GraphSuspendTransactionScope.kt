package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * 코루틴 그래프 트랜잭션 블록에서 사용할 수 있는 연산 범위.
 *
 * ```kotlin
 * val edge = ops.suspendTransaction {
 *     val alice = createVertex("Person", mapOf("name" to "Alice"))
 *     val bob = createVertex("Person", mapOf("name" to "Bob"))
 *     createEdge(alice.id, bob.id, "KNOWS")
 * }
 * ```
 *
 * 이번 API는 capability contract를 먼저 제공한다. 각 백엔드는 실제 코루틴 트랜잭션 의미를
 * 보장할 수 있을 때 [GraphSuspendTransactionalOperations]를 구현한다.
 */
@GraphTransactionDsl
interface GraphSuspendTransactionScope :
    GraphSuspendVertexRepository,
    GraphSuspendEdgeRepository

/**
 * 동기 [GraphTransactionScope]를 코루틴 트랜잭션 범위로 노출하는 어댑터.
 *
 * Neo4j Java Driver, Memgraph Bolt, TinkerGraph처럼 동기 트랜잭션 API가 원자성의 실제 소유자인
 * 백엔드에서 suspend DSL을 제공할 때 사용한다. 호출자는 이 타입을 직접 생성하기보다
 * [asSuspendTransactionScope]를 사용한다.
 */
class BlockingGraphSuspendTransactionScope(
    private val delegate: GraphTransactionScope,
): GraphSuspendTransactionScope {

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        delegate.createVertex(label, properties)

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? =
        delegate.findVertexById(label, id)

    override suspend fun findVertexById(id: GraphElementId): GraphVertex? =
        delegate.findVertexById(id)

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> =
        delegate.findVerticesByLabel(label, filter).asFlow()

    override suspend fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? =
        delegate.updateVertex(label, id, properties)

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id)

    override suspend fun countVertices(label: String): Long =
        delegate.countVertices(label)

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge =
        delegate.createEdge(fromId, toId, label, properties)

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
        delegate.findEdgesByLabel(label, filter).asFlow()

    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> =
        delegate.findEdgesByStartId(startId, edgeLabel).asFlow()

    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> =
        delegate.findEdgesByEndId(endId, edgeLabel).asFlow()

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id)
}

/**
 * 동기 트랜잭션 범위를 코루틴 트랜잭션 범위로 변환한다.
 */
fun GraphTransactionScope.asSuspendTransactionScope(): GraphSuspendTransactionScope =
    BlockingGraphSuspendTransactionScope(this)

/**
 * 코루틴 트랜잭션을 실제로 지원하는 [GraphSuspendOperations] 구현체가 구현하는 capability interface.
 */
interface GraphSuspendTransactionalOperations {
    /**
     * [block]을 하나의 백엔드 트랜잭션으로 실행한다.
     *
     * 구현체는 성공 시 commit, 실패 시 rollback 후 원래 예외를 다시 던져야 한다.
     */
    suspend fun <T> suspendTransaction(block: suspend GraphSuspendTransactionScope.() -> T): T
}

/**
 * [GraphSuspendOperations]에서 코루틴 트랜잭션 DSL을 실행한다.
 *
 * 구현체가 [GraphSuspendTransactionalOperations]를 구현하지 않으면 auto-commit fallback을 사용하지 않고
 * 명시적으로 [UnsupportedOperationException]을 던진다.
 */
suspend fun <T> GraphSuspendOperations.suspendTransaction(
    block: suspend GraphSuspendTransactionScope.() -> T,
): T {
    val transactional = this as? GraphSuspendTransactionalOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support suspend graph transactions."
        )
    return transactional.suspendTransaction(block)
}

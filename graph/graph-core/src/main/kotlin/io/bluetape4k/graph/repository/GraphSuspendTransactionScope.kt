package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * Operation scope available inside a coroutine graph transaction block.
 *
 * ```kotlin
 * val edge = ops.suspendTransaction {
 *     val alice = createVertex("Person", mapOf("name" to "Alice"))
 *     val bob = createVertex("Person", mapOf("name" to "Bob"))
 *     createEdge(alice.id, bob.id, "KNOWS")
 * }
 * ```
 *
 * This API exposes the capability contract first. Each backend implements
 * [GraphSuspendTransactionalOperations] only when it can provide real coroutine transaction semantics.
 */
@GraphTransactionDsl
interface GraphSuspendTransactionScope :
    GraphSuspendVertexRepository,
    GraphSuspendEdgeRepository

/**
 * Adapter that exposes a synchronous [GraphTransactionScope] as a coroutine transaction scope.
 *
 * Use this when a backend, such as the Neo4j Java Driver, Memgraph Bolt, or TinkerGraph,
 * owns atomicity through a synchronous transaction API but still exposes a suspend DSL.
 * Callers should use [asSuspendTransactionScope] instead of constructing this type directly.
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
 * Converts a synchronous transaction scope into a coroutine transaction scope.
 */
fun GraphTransactionScope.asSuspendTransactionScope(): GraphSuspendTransactionScope =
    BlockingGraphSuspendTransactionScope(this)

/**
 * Capability interface implemented by [GraphSuspendOperations] implementations with real coroutine transactions.
 */
interface GraphSuspendTransactionalOperations {
    /**
     * Runs [block] as one backend transaction.
     *
     * Implementations must commit on success, roll back on failure, and rethrow the original exception.
     */
    suspend fun <T> suspendTransaction(block: suspend GraphSuspendTransactionScope.() -> T): T
}

/**
 * Executes the coroutine transaction DSL on [GraphSuspendOperations].
 *
 * If the implementation does not implement [GraphSuspendTransactionalOperations], this function
 * explicitly throws [UnsupportedOperationException] instead of using an auto-commit fallback.
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

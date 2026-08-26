package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphTransactionScope
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadChunkedOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadMergeOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadSchemaManagementOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadTransactionalOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

private fun <T> unsupportedFuture(operation: String): CompletableFuture<T> =
    CompletableFuture.failedFuture(
        UnsupportedOperationException("Virtual Thread surface does not support $operation."),
    )

/**
 * [GraphMergeOperations]를 Virtual Thread `CompletableFuture` API로 감싼다.
 *
 * nullable delegate는 통합 facade가 unsupported backend를 명시적으로 실패시키기 위해
 * 내부에서만 사용한다. public 진입점은 [GraphMergeOperations]를 요구한다.
 */
class VirtualThreadMergeAdapter internal constructor(
    private val delegate: GraphMergeOperations?,
) : GraphVirtualThreadMergeOperations {

    companion object : KLogging()

    override fun mergeVertexAsync(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex> =
        delegate?.let { merge ->
            virtualFutureOf { merge.mergeVertex(label, matchProperties, setProperties) }
        } ?: unsupportedFuture("mergeVertexAsync")

    override fun mergeEdgeAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): CompletableFuture<GraphEdge> =
        delegate?.let { merge ->
            virtualFutureOf { merge.mergeEdge(fromId, toId, label, matchProperties, setProperties) }
        } ?: unsupportedFuture("mergeEdgeAsync")
}

/**
 * [GraphSchemaManager]를 Virtual Thread schema surface로 감싼다.
 */
class VirtualThreadSchemaAdapter internal constructor(
    private val delegate: GraphSchemaManager?,
) : GraphVirtualThreadSchemaManagementOperations {

    companion object : KLogging()

    override fun createIndexAsync(label: String, property: String): CompletableFuture<Unit> =
        delegate?.let { schema -> virtualFutureOf { schema.createIndex(label, property) } }
            ?: unsupportedFuture("createIndexAsync")

    override fun createUniqueConstraintAsync(label: String, property: String): CompletableFuture<Unit> =
        delegate?.let { schema -> virtualFutureOf { schema.createUniqueConstraint(label, property) } }
            ?: unsupportedFuture("createUniqueConstraintAsync")

    override fun dropIndexAsync(label: String, property: String): CompletableFuture<Unit> =
        delegate?.let { schema -> virtualFutureOf { schema.dropIndex(label, property) } }
            ?: unsupportedFuture("dropIndexAsync")

    override fun listIndexesAsync(): CompletableFuture<List<GraphIndex>> =
        delegate?.let { schema -> virtualFutureOf { schema.listIndexes() } }
            ?: unsupportedFuture("listIndexesAsync")

    override fun listConstraintsAsync(): CompletableFuture<List<GraphConstraint>> =
        delegate?.let { schema -> virtualFutureOf { schema.listConstraints() } }
            ?: unsupportedFuture("listConstraintsAsync")
}

/**
 * [GraphTransactionalOperations]를 하나의 Virtual Thread transaction block으로 감싼다.
 *
 * `CompletableFuture.cancel(true)`는 이미 시작된 backend 작업의 중단을 보장하지 않으므로
 * cancellation은 future 상태로만 관찰한다. delegate lifecycle은 이 adapter가 소유하지 않는다.
 */
class VirtualThreadTransactionalAdapter internal constructor(
    private val delegate: GraphTransactionalOperations?,
) : GraphVirtualThreadTransactionalOperations {

    companion object : KLogging()

    @Suppress("UNCHECKED_CAST")
    override fun <T> transactionAsync(block: GraphTransactionScope.() -> T): CompletableFuture<T> =
        virtualFutureOfNullable {
            delegate?.transaction(block)
                ?: throw UnsupportedOperationException("Virtual Thread surface does not support transactionAsync.")
        } as CompletableFuture<T>
}

/**
 * 동기 vertex/edge chunk sequence를 Virtual Thread에서 소비하는 adapter다.
 * 반환 list는 chunk 경계를 유지하며 sequence가 [AutoCloseable]이면 항상 닫는다.
 */
class VirtualThreadChunkedAdapter internal constructor(
    private val delegate: GraphOperations,
) : GraphVirtualThreadChunkedOperations {

    companion object : KLogging()

    override fun findVerticesByLabelChunkedAsync(
        label: String,
        filter: Map<String, Any?>,
        chunkSize: Int,
    ): CompletableFuture<List<List<GraphVertex>>> =
        virtualFutureOf {
            collectChunks(delegate.findVerticesByLabelChunked(label, filter, chunkSize))
        }

    override fun findEdgesByLabelChunkedAsync(
        label: String,
        filter: Map<String, Any?>,
        chunkSize: Int,
    ): CompletableFuture<List<List<GraphEdge>>> =
        virtualFutureOf {
            collectChunks(delegate.findEdgesByLabelChunked(label, filter, chunkSize))
        }

    private fun <T> collectChunks(sequence: Sequence<List<T>>): List<List<T>> =
        if (sequence is AutoCloseable) {
            try {
                sequence.toList()
            } finally {
                sequence.close()
            }
        } else {
            sequence.toList()
        }
}

/** Wraps a synchronous merge capability in a Virtual Thread adapter. */
fun GraphMergeOperations.asVirtualThreadMerge(): GraphVirtualThreadMergeOperations =
    VirtualThreadMergeAdapter(this)

/** Wraps a synchronous schema manager in a Virtual Thread adapter. */
fun GraphSchemaManager.asVirtualThreadSchema(): GraphVirtualThreadSchemaManagementOperations =
    VirtualThreadSchemaAdapter(this)

/** Wraps a synchronous transaction capability in a Virtual Thread adapter. */
fun GraphTransactionalOperations.asVirtualThreadTransactional(): GraphVirtualThreadTransactionalOperations =
    VirtualThreadTransactionalAdapter(this)

/** Wraps synchronous chunked graph reads in a Virtual Thread adapter. */
fun GraphOperations.asVirtualThreadChunked(): GraphVirtualThreadChunkedOperations =
    VirtualThreadChunkedAdapter(this)

package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import java.util.Collections
import java.util.IdentityHashMap

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
     * [block]을 하나의 backend transaction으로 실행한다.
     *
     * 구현체는 성공 시 commit하고 실패 시 rollback한 뒤 원래 예외를 다시 던져야 한다. 반환된 최상위 [Flow]는
     * commit 전에 materialize한다. [Pair], [Triple], [Map], [Collection], 배열 안에 중첩된 [Flow]는 transaction
     * 결과 계약에 포함하지 않으므로 [block] 안에서 명시적으로 materialize한 뒤 반환해야 한다. [Sequence]와
     * 임의 사용자 wrapper/data class 내부는 reflection이나 iteration으로 검사하지 않으므로 호출자 책임이다.
     */
    suspend fun <T> suspendTransaction(block: suspend GraphSuspendTransactionScope.() -> T): T
}

/**
 * 트랜잭션 결과의 공통 `Flow` 계약을 적용한다.
 *
 * 최상위 `Flow`는 commit 전에 수집한 뒤 재수집 가능한 `Flow`로 반환한다. `Pair`, `Triple`, `Map`,
 * `Collection`, 배열처럼 표준 컨테이너 안에 들어 있는 중첩 `Flow`는 transaction scope 밖으로 escape할 수
 * 있으므로 `IllegalArgumentException`으로 거부한다. `Sequence`와 임의 사용자 wrapper/data class 내부는
 * reflection이나 iteration으로 검사하지 않으므로 호출자가 명시적으로 materialize해야 한다. 중첩 `Flow`가
 * 필요하면 transaction block 안에서 `toList()` 등으로 명시적으로 materialize한 뒤 반환해야 한다.
 *
 * @throws IllegalArgumentException 표준 컨테이너 안에 중첩된 `Flow`가 있을 때
 */
suspend fun <T> materializeSuspendTransactionResult(result: T): T {
    if (result !is Flow<*>) {
        requireNoNestedFlow(result)
        return result
    }

    val values = result.toList()
    values.forEachIndexed { index, value ->
        requireNoNestedFlow(value, "result[$index]")
    }

    @Suppress("UNCHECKED_CAST")
    return values.asFlow() as T
}

private fun requireNoNestedFlow(
    value: Any?,
    path: String = "result",
    visited: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap()),
) {
    if (value == null) return
    if (value is Flow<*>) {
        throw IllegalArgumentException(
            "suspendTransaction result contains a nested Flow at $path. " +
                "Materialize it inside the transaction before returning."
        )
    }
    if (!visited.add(value)) return

    when (value) {
        is Pair<*, *> -> {
            requireNoNestedFlow(value.first, "$path.first", visited)
            requireNoNestedFlow(value.second, "$path.second", visited)
        }

        is Triple<*, *, *> -> {
            requireNoNestedFlow(value.first, "$path.first", visited)
            requireNoNestedFlow(value.second, "$path.second", visited)
            requireNoNestedFlow(value.third, "$path.third", visited)
        }

        is Map<*, *> -> value.entries.forEach { (key, entryValue) ->
            requireNoNestedFlow(key, "$path.key", visited)
            requireNoNestedFlow(entryValue, "$path[$key]", visited)
        }

        is Collection<*> -> value.forEachIndexed { index, entryValue ->
            requireNoNestedFlow(entryValue, "$path[$index]", visited)
        }

        is Array<*> -> value.forEachIndexed { index, entryValue ->
            requireNoNestedFlow(entryValue, "$path[$index]", visited)
        }
    }
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

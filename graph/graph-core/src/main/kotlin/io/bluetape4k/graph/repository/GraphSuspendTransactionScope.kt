package io.bluetape4k.graph.repository

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

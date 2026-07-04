package io.bluetape4k.graph.repository

/**
 * Marker for graph transaction DSL receivers.
 *
 * Transaction blocks expose only vertex and edge CRUD. Session lifecycle commands,
 * such as graph creation or deletion, stay outside the DSL because DDL and auto-commit
 * semantics differ by backend.
 */
@DslMarker
annotation class GraphTransactionDsl

/**
 * Operation scope available inside a synchronous graph transaction block.
 *
 * ```kotlin
 * val edge = ops.transaction {
 *     val alice = createVertex("Person", mapOf("name" to "Alice"))
 *     val bob = createVertex("Person", mapOf("name" to "Bob"))
 *     createEdge(alice.id, bob.id, "KNOWS")
 * }
 * ```
 *
 * Backends must commit when the block completes normally and roll back when it throws.
 */
@GraphTransactionDsl
interface GraphTransactionScope :
    GraphVertexRepository,
    GraphEdgeRepository

/**
 * Capability interface implemented by [GraphOperations] implementations with real synchronous transactions.
 *
 * This keeps source compatibility for existing implementations and test fakes by
 * avoiding new members on [GraphOperations].
 */
interface GraphTransactionalOperations {
    /**
     * Runs [block] as one backend transaction.
     *
     * Implementations must commit on success, roll back on failure, and rethrow the original exception.
     */
    fun <T> transaction(block: GraphTransactionScope.() -> T): T
}

/**
 * Executes the synchronous transaction DSL on [GraphOperations].
 *
 * If the implementation does not implement [GraphTransactionalOperations], this function
 * explicitly throws [UnsupportedOperationException] instead of using an auto-commit fallback.
 * A silent fallback would make callers believe atomicity is guaranteed.
 */
fun <T> GraphOperations.transaction(block: GraphTransactionScope.() -> T): T {
    val transactional = this as? GraphTransactionalOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support graph transactions."
        )
    return transactional.transaction(block)
}

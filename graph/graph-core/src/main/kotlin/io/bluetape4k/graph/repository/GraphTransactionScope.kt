package io.bluetape4k.graph.repository

/**
 * 그래프 트랜잭션 DSL 수신 객체를 구분하는 마커.
 *
 * 트랜잭션 블록 안에서는 정점/간선 CRUD만 노출한다. 그래프 생성/삭제 같은
 * session lifecycle 명령은 백엔드별 DDL/auto-commit 의미가 달라 DSL 범위에서 제외한다.
 */
@DslMarker
annotation class GraphTransactionDsl

/**
 * 동기 그래프 트랜잭션 블록에서 사용할 수 있는 연산 범위.
 *
 * ```kotlin
 * val edge = ops.transaction {
 *     val alice = createVertex("Person", mapOf("name" to "Alice"))
 *     val bob = createVertex("Person", mapOf("name" to "Bob"))
 *     createEdge(alice.id, bob.id, "KNOWS")
 * }
 * ```
 *
 * 블록이 정상 종료되면 백엔드는 변경을 커밋하고, 예외가 발생하면 롤백해야 한다.
 */
@GraphTransactionDsl
interface GraphTransactionScope :
    GraphVertexRepository,
    GraphEdgeRepository

/**
 * 동기 트랜잭션을 실제로 지원하는 [GraphOperations] 구현체가 구현하는 capability interface.
 *
 * [GraphOperations] 자체에 멤버를 추가하지 않아 기존 구현체와 테스트 fake의 source compatibility를 유지한다.
 */
interface GraphTransactionalOperations {
    /**
     * [block]을 하나의 백엔드 트랜잭션으로 실행한다.
     *
     * 구현체는 성공 시 commit, 실패 시 rollback 후 원래 예외를 다시 던져야 한다.
     */
    fun <T> transaction(block: GraphTransactionScope.() -> T): T
}

/**
 * [GraphOperations]에서 동기 트랜잭션 DSL을 실행한다.
 *
 * 구현체가 [GraphTransactionalOperations]를 구현하지 않으면 auto-commit fallback을 사용하지 않고
 * 명시적으로 [UnsupportedOperationException]을 던진다. 조용한 fallback은 호출자가 원자성을
 * 보장받는다고 착각하게 만들 수 있기 때문이다.
 */
fun <T> GraphOperations.transaction(block: GraphTransactionScope.() -> T): T {
    val transactional = this as? GraphTransactionalOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support graph transactions."
        )
    return transactional.transaction(block)
}

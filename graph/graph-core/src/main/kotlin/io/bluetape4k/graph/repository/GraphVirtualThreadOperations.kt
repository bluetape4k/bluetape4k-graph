package io.bluetape4k.graph.repository

/**
 * Virtual Thread API의 통합 graph facade다.
 *
 * `*Async` method로 session, CRUD, traversal, portable algorithm을 하나의
 * `CompletableFuture` 기반 interface로 제공한다. blocking [GraphSession] method는
 * 포함하지 않으며 [close]는 facade만 닫는다.
 *
 * [capabilities]는 현재 facade가 실제로 호출할 수 있는 async capability를
 * 보고하고, [GraphVirtualThreadCapabilitiesOperations.delegateCapabilities]는
 * 외부에서 소유한 synchronous delegate의 전체 매핑을 보존한다. delegate가
 * `MERGE`, `SCHEMA`, `TRANSACTION`을 구현하면 대응하는 optional `*Async` surface가
 * 함께 제공된다. 모든 chunk API는 future 완료 시 chunk 경계를 보존한 list를
 * 반환하며, 결과 자체는 materialized된다. 진정한 streaming cursor는 synchronous
 * API를 사용해야 한다.
 *
 * delegate의 소유권은 호출자에게 있다. 따라서 [close]는 delegate를 조기
 * 종료하지 않으며, delegate의 close 책임도 호출자에게 남는다.
 *
 * ```kotlin
 * val vtOps: GraphVirtualThreadOperations = ops.asVirtualThread()
 * if (vtOps.capabilities().supports(GraphCapability.GRAPH_ALGORITHM)) {
 *     val vertex = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
 * }
 * ```
 */
interface GraphVirtualThreadOperations :
    AutoCloseable,
    GraphVirtualThreadCapabilitiesOperations,
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    GraphVirtualThreadEdgeRepository,
    GraphVirtualThreadTraversalRepository,
    GraphVirtualThreadAlgorithmRepository {

    /** 기본 구현은 facade가 실제로 노출하는 async capability를 계산한다. */
    override fun capabilities(): GraphCapabilities = surfaceCapabilities()

    /** 기본 구현은 facade 자체에 선언된 async capability를 계산한다. */
    override fun surfaceCapabilities(): GraphCapabilities = GraphCapabilities.from(this)
}

package io.bluetape4k.graph.repository

/**
 * Virtual Thread API의 통합 graph facade다.
 *
 * `*Async` method로 session, CRUD, traversal, portable algorithm을 하나의
 * `CompletableFuture` 기반 interface로 제공한다. blocking [GraphSession] method는
 * 포함하지 않으며 [close]는 facade만 닫는다.
 *
 * [capabilities]는 외부에서 소유한 synchronous delegate의 capability 매핑을
 * 보존해 선택 기능을 호출하기 전에 확인할 수 있게 한다. 다만 현재 facade는
 * `MERGE`, `SCHEMA`, `TRANSACTION`, `CHUNKED_READ`용 별도 `*Async` method를
 * 노출하지 않는다. 이 optional contract는 thread affinity, callback 경계,
 * bounded read semantics가 확정될 때까지 synchronous/suspend API 또는
 * graph-io Virtual Thread adapter를 사용해야 하며, capability flag만으로
 * asynchronous method가 있다고 추측해서는 안 된다. `BOUNDED_CHUNKED_*`도
 * delegate source 실행 보장을 그대로 투영할 뿐 Virtual Thread async method를
 * 추가하지 않는다.
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

    /** 기본 구현은 facade 자체에 선언된 async capability를 계산한다. */
    override fun capabilities(): GraphCapabilities = GraphCapabilities.from(this)
}

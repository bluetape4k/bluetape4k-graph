package io.bluetape4k.graph.repository

/**
 * Virtual Thread 기반 그래프 통합 Facade.
 *
 * 비동기 Virtual Thread API(`*Async` 메서드)를 하나의 인터페이스로 제공한다.
 * 동기 블로킹 메서드([GraphSession])는 포함하지 않는다. 세션 lifecycle은 [close]로 관리한다.
 *
 * ```kotlin
 * val vtOps: GraphVirtualThreadOperations = ops.asVirtualThread()
 * val vertex = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
 * ```
 */
interface GraphVirtualThreadOperations:
    AutoCloseable,
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    GraphVirtualThreadEdgeRepository,
    GraphVirtualThreadTraversalRepository,
    GraphVirtualThreadAlgorithmRepository

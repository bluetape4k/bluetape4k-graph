package io.bluetape4k.graph.repository

/**
 * Virtual Thread 기반 그래프 통합 Facade.
 *
 * [GraphSession] 의 동기 lifecycle 메서드와
 * 전체 비동기 Virtual Thread API(`*Async` 메서드)를 하나의 인터페이스로 제공한다.
 *
 * ```kotlin
 * val vtOps: GraphVirtualThreadOperations = ops.asVirtualThread()
 * val vertex = vtOps.createVertexAsync("Person", mapOf("name" to "Alice")).join()
 * ```
 */
interface GraphVirtualThreadOperations:
    GraphSession,
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    GraphVirtualThreadEdgeRepository,
    GraphVirtualThreadTraversalRepository,
    GraphVirtualThreadAlgorithmRepository

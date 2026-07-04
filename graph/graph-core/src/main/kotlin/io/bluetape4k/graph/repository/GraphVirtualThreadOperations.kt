package io.bluetape4k.graph.repository

/**
 * Unified graph facade for the Virtual Thread API.
 *
 * Provides the asynchronous Virtual Thread API (`*Async` methods) as one interface.
 * It does not include blocking [GraphSession] methods. Manage the session lifecycle with [close].
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

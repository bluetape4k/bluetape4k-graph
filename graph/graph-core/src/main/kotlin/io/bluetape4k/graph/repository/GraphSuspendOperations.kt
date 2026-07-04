package io.bluetape4k.graph.repository

/**
 * Coroutine facade for graph databases.
 * Backends such as AGE and Neo4j implement this interface.
 *
 * @see GraphOperations synchronous blocking variant
 *
 * ### Usage
 * ```kotlin
 * // ops may be AgeGraphSuspendOperations, Neo4jGraphSuspendOperations, and so on.
 * val ops: GraphSuspendOperations = ...
 *
 * ops.createGraph("social")
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * ops.createEdge(alice.id, bob.id, "FOLLOWS")
 *
 * val neighbors = ops.neighbors(alice.id).toList()  // Flow to List
 * ```
 */
interface GraphSuspendOperations :
    GraphSuspendSession,
    GraphSuspendVertexRepository,
    GraphSuspendEdgeRepository,
    GraphSuspendGenericRepository

package io.bluetape4k.graph.repository

/**
 * Unified graph database facade for the blocking API.
 * Backends such as AGE and Neo4j implement this interface.
 *
 * @see GraphSuspendOperations coroutine API with suspend functions and Flow
 *
 * ### Usage
 * ```kotlin
 * // ops can be AgeGraphOperations, Neo4jGraphOperations, TinkerGraphOperations, and so on.
 * val ops: GraphOperations = TinkerGraphOperations()
 *
 * ops.createGraph("social")
 *
 * val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
 * val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
 * val edge  = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
 *
 * val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 * val path      = ops.shortestPath(alice.id, bob.id, PathOptions())
 * ```
 */
interface GraphOperations :
    GraphSession,
    GraphVertexRepository,
    GraphEdgeRepository,
    GraphGenericRepository

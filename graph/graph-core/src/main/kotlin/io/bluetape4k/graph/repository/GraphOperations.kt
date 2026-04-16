package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade (동기 방식).
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 *
 * @see GraphSuspendOperations 코루틴(suspend + Flow) 방식
 *
 * ### 사용 예제
 * ```kotlin
 * // ops는 AgeGraphOperations, Neo4jGraphOperations, TinkerGraphOperations 등
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

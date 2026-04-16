package io.bluetape4k.graph.repository

/**
 * 순회(traversal) + 분석(algorithm) 을 묶은 코루틴 합성 인터페이스.
 *
 * [GraphSuspendOperations] 의존성을 좁히고 싶을 때 이 타입을 직접 주입할 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * suspend fun analyze(repo: GraphSuspendGenericRepository) {
 *     val path = repo.shortestPath(a, b)
 *     val scores = repo.pageRank().toList()
 * }
 * ```
 */
interface GraphSuspendGenericRepository :
    GraphSuspendTraversalRepository,
    GraphSuspendAlgorithmRepository

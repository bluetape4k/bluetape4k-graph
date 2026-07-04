package io.bluetape4k.graph.repository

/**
 * Coroutine composite interface for traversal and algorithm operations.
 *
 * Inject this type directly when callers need a narrower dependency than [GraphSuspendOperations].
 *
 * ### Usage
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

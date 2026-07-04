package io.bluetape4k.graph.repository

/**
 * Synchronous composite interface for traversal and algorithm operations.
 *
 * Inject this type directly when callers need a narrower dependency than [GraphOperations].
 *
 * ### Usage
 * ```kotlin
 * fun analyze(repo: GraphGenericRepository) {
 *     val path = repo.shortestPath(a, b)
 *     val scores = repo.pageRank()
 * }
 * ```
 */
interface GraphGenericRepository : GraphTraversalRepository, GraphAlgorithmRepository

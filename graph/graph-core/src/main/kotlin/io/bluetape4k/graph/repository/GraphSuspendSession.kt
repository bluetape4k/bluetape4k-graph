package io.bluetape4k.graph.repository

/**
 * Coroutine graph database session management.
 *
 * Ownership: [close] does not close externally injected databases or drivers.
 * Connection pool and driver lifecycles are owned by the Spring container or caller.
 *
 * ```kotlin
 * runBlocking {
 *     ops.createGraph("social")
 *     ops.graphExists("social")  // true
 *     ops.dropGraph("social")
 *     ops.graphExists("social")  // false
 * }
 * ```
 *
 * @see GraphSession synchronous blocking variant
 */
interface GraphSuspendSession : AutoCloseable {
    /**
     * Creates a graph with the given name.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ```
     *
     * @param name graph name to create.
     * @see GraphSession.createGraph synchronous version
     */
    suspend fun createGraph(name: String)

    /**
     * Drops a graph with the given name.
     *
     * ```kotlin
     * ops.dropGraph("social")
     * ```
     *
     * @param name graph name to drop.
     * @see GraphSession.dropGraph synchronous version
     */
    suspend fun dropGraph(name: String)

    /**
     * Checks whether a graph with the given name exists.
     *
     * ```kotlin
     * val exists = ops.graphExists("social")  // true / false
     * ```
     *
     * @param name graph name to check.
     * @return `true` when the graph exists, otherwise `false`.
     * @see GraphSession.graphExists synchronous version
     */
    suspend fun graphExists(name: String): Boolean
}

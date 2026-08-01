package io.bluetape4k.graph.repository

/**
 * Coroutine graph database session management.
 *
 * Ownership: [close] does not close externally injected databases or drivers.
 * Connection pool and driver lifecycles are owned by the Spring container or caller.
 * Named graph catalog를 제공하지 않는 backend는 `createGraph(name)`을 논리적 current-name
 * 선택으로 해석하며, `dropGraph(name)`은 이름이 일치할 때만 current graph를 비운다.
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
     * @param name 생성할 graph name.
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
     * @param name 삭제할 graph name.
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
     * @param name 존재 여부를 확인할 graph name.
     * @return `true` when the graph exists, otherwise `false`.
     * @see GraphSession.graphExists synchronous version
     */
    suspend fun graphExists(name: String): Boolean
}

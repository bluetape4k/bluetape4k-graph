package io.bluetape4k.graph.repository

/**
 * Manages graph database sessions with the blocking API.
 *
 * Ownership: [close] does not close externally supplied databases or drivers.
 * The Spring container or caller owns connection pool and driver lifecycles.
 *
 * ```kotlin
 * ops.createGraph("social")          // create graph
 * ops.graphExists("social")          // true
 * ops.dropGraph("social")            // drop graph
 * ops.graphExists("social")          // false
 * ```
 */
interface GraphSession : AutoCloseable {
    /**
     * Creates the graph with the given name.
     *
     * Calling this for an existing graph may throw [io.bluetape4k.graph.GraphAlreadyExistsException]
     * or may be ignored by the backend implementation.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ops.graphExists("social")  // true
     * ```
     *
     * @param name graph name to create.
     */
    fun createGraph(name: String)

    /**
     * Drops the graph with the given name.
     *
     * Calling this for a missing graph may throw [io.bluetape4k.graph.GraphNotFoundException]
     * or may be ignored by the backend implementation.
     *
     * ```kotlin
     * ops.dropGraph("social")
     * ops.graphExists("social")  // false
     * ```
     *
     * @param name graph name to drop.
     */
    fun dropGraph(name: String)

    /**
     * Checks whether the graph with the given name exists.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ops.graphExists("social")  // true
     * ops.dropGraph("social")
     * ops.graphExists("social")  // false
     * ```
     *
     * @param name graph name to check.
     * @return `true` when the graph exists, otherwise `false`.
     */
    fun graphExists(name: String): Boolean
}

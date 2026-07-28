package io.bluetape4k.graph.repository

/**
 * blocking API로 graph database session을 관리한다.
 *
 * Ownership: [close]는 외부에서 주입된 database나 driver를 닫지 않는다.
 * connection pool과 driver lifecycle은 Spring container 또는 caller가 소유한다.
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
     * 주어진 name으로 graph를 생성한다.
     *
     * 이미 존재하는 graph에 대해 호출하면 [io.bluetape4k.graph.GraphAlreadyExistsException]이 발생할 수 있으며
     * backend implementation에 따라 무시될 수도 있다.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ops.graphExists("social")  // true
     * ```
     *
     * @param name 생성할 graph name.
     */
    fun createGraph(name: String)

    /**
     * 주어진 name의 graph를 삭제한다.
     *
     * 없는 graph에 대해 호출하면 [io.bluetape4k.graph.GraphNotFoundException]이 발생할 수 있으며
     * backend implementation에 따라 무시될 수도 있다.
     *
     * ```kotlin
     * ops.dropGraph("social")
     * ops.graphExists("social")  // false
     * ```
     *
     * @param name 삭제할 graph name.
     */
    fun dropGraph(name: String)

    /**
     * 주어진 name의 graph가 존재하는지 확인한다.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ops.graphExists("social")  // true
     * ops.dropGraph("social")
     * ops.graphExists("social")  // false
     * ```
     *
     * @param name 존재 여부를 확인할 graph name.
     * @return `true` when the graph exists, otherwise `false`.
     */
    fun graphExists(name: String): Boolean
}

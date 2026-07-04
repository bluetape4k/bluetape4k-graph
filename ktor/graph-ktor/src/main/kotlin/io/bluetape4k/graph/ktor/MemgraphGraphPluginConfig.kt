package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver

/**
 * Configures [GraphPlugin] to use a Memgraph backend.
 *
 * ## Behavior / Contract
 * - [driver] is a caller-owned resource; this helper does not close it.
 * - [database] defaults to `"memgraph"`, matching the default used by `MemgraphGraphOperations`
 *   and Spring Boot properties.
 *
 * ```kotlin
 * fun Application.module(driver: Driver) {
 *     install(GraphPlugin) {
 *         memgraph(driver)
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.memgraph(
    driver: Driver,
    database: String = "memgraph",
): GraphPluginConfig = apply {
    database.requireNotBlank("database")

    val graphOperations = MemgraphGraphOperations(driver, database)
    val graphSuspendOperations = MemgraphGraphSuspendOperations(driver, database)

    configure(
        backendName = "memgraph",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
    )
}

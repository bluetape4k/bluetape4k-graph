package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver

/**
 * Configures [GraphPlugin] to use a Neo4j backend.
 *
 * ## Behavior / Contract
 * - [driver] is a caller-owned resource; this helper does not close it.
 * - [database] must not be blank.
 *
 * ```kotlin
 * fun Application.module(driver: Driver) {
 *     install(GraphPlugin) {
 *         neo4j(driver, database = "neo4j")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.neo4j(
    driver: Driver,
    database: String = "neo4j",
): GraphPluginConfig = apply {
    database.requireNotBlank("database")

    val graphOperations = Neo4jGraphOperations(driver, database)
    val graphSuspendOperations = Neo4jGraphSuspendOperations(driver, database)

    configure(
        backendName = "neo4j",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
    )
}

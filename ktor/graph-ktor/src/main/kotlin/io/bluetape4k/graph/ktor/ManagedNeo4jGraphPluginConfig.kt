package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

/**
 * Ktor DSL for creating a Neo4j driver owned by [GraphPlugin].
 *
 * ## Behavior / Contract
 * - [uri] and [database] must not be blank.
 * - A blank [username] creates the driver with `AuthTokens.none()`.
 * - The driver created by this DSL is plugin-owned and is closed on `ApplicationStopped`.
 * - The existing `neo4j(driver, database)` helper keeps its caller-owned driver contract.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     neo4j {
 *         uri = "bolt://localhost:7687"
 *         username = "neo4j"
 *         password = "secret"
 *     }
 * }
 * ```
 */
class ManagedNeo4jGraphPluginConfig {
    var uri: String = "bolt://localhost:7687"
    var username: String = ""
    var password: String = ""
    var database: String = "neo4j"
}

/**
 * Configures [GraphPlugin] with a plugin-owned Neo4j driver.
 */
fun GraphPluginConfig.neo4j(
    configure: ManagedNeo4jGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedNeo4jGraphPluginConfig().apply(configure)
    props.uri.requireNotBlank("uri")
    props.database.requireNotBlank("database")

    val authToken = if (props.username.isBlank()) {
        AuthTokens.none()
    } else {
        AuthTokens.basic(props.username, props.password)
    }
    val driver = GraphDatabase.driver(props.uri, authToken)
    val graphOperations = Neo4jGraphOperations(driver, props.database)
    val graphSuspendOperations = Neo4jGraphSuspendOperations(driver, props.database)

    configure(
        backendName = "managedNeo4j",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("Neo4jGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("Neo4jGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
            GraphPluginCloseAction("Neo4jDriver") {
                driver.close()
            },
        ),
    )
}

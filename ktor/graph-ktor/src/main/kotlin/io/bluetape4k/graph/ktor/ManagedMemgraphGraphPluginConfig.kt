package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

/**
 * Ktor DSL for creating a Memgraph driver owned by [GraphPlugin].
 *
 * ## Behavior / Contract
 * - Memgraph uses the Neo4j Bolt-compatible Java driver.
 * - [uri] and [database] must not be blank.
 * - A blank [username] creates an unauthenticated driver.
 * - The driver created by this DSL is plugin-owned and is closed on `ApplicationStopped`.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     memgraph {
 *         uri = "bolt://localhost:7687"
 *         database = "memgraph"
 *     }
 * }
 * ```
 */
class ManagedMemgraphGraphPluginConfig {
    var uri: String = "bolt://localhost:7687"
    var username: String = ""
    var password: String = ""
    var database: String = "memgraph"
}

/**
 * Configures [GraphPlugin] with a plugin-owned Memgraph driver.
 */
fun GraphPluginConfig.memgraph(
    configure: ManagedMemgraphGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedMemgraphGraphPluginConfig().apply(configure)
    props.uri.requireNotBlank("uri")
    props.database.requireNotBlank("database")

    val authToken = if (props.username.isBlank()) {
        AuthTokens.none()
    } else {
        AuthTokens.basic(props.username, props.password)
    }
    val driver = GraphDatabase.driver(props.uri, authToken)
    val graphOperations = MemgraphGraphOperations(driver, props.database)
    val graphSuspendOperations = MemgraphGraphSuspendOperations(driver, props.database)

    configure(
        backendName = "managedMemgraph",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("MemgraphGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("MemgraphGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
            GraphPluginCloseAction("MemgraphDriver") {
                driver.close()
            },
        ),
    )
}

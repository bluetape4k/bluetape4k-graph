package io.bluetape4k.graph.ktor

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber

/**
 * Ktor DSL for creating a FalkorDB driver owned by [GraphPlugin].
 *
 * ## Behavior / Contract
 * - [host] and [graphName] must not be blank.
 * - [port] must be positive.
 * - A blank [username] uses unauthenticated `FalkorDB.driver(host, port)`.
 * - The driver created by this DSL is plugin-owned and is closed on `ApplicationStopped`.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     falkorDB {
 *         host = "localhost"
 *         port = 6379
 *         graphName = "social"
 *     }
 * }
 * ```
 */
class ManagedFalkorDBGraphPluginConfig {
    var host: String = "localhost"
    var port: Int = 6379
    var username: String = ""
    var password: String = ""
    var graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME
}

/**
 * Configures [GraphPlugin] with a plugin-owned FalkorDB driver.
 */
fun GraphPluginConfig.falkorDB(
    configure: ManagedFalkorDBGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedFalkorDBGraphPluginConfig().apply(configure)
    props.host.requireNotBlank("host")
    props.port.requirePositiveNumber("port")
    props.graphName.requireNotBlank("graphName")

    val driver = if (props.username.isBlank()) {
        FalkorDB.driver(props.host, props.port)
    } else {
        FalkorDB.driver(props.host, props.port, props.username, props.password)
    }
    val graphOperations = FalkorDBGraphOperations(driver, props.graphName)
    val graphSuspendOperations = FalkorDBGraphSuspendOperations(driver, props.graphName)

    configure(
        backendName = "managedFalkorDB",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("FalkorDBGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("FalkorDBGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
            GraphPluginCloseAction("FalkorDBDriver") {
                driver.close()
            },
        ),
    )
}

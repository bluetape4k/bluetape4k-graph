package io.bluetape4k.graph.ktor

import com.falkordb.Driver
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank

/**
 * Configures [GraphPlugin] to use a FalkorDB backend.
 *
 * ## Behavior / Contract
 * - [driver] is a caller-owned resource; this helper does not close it.
 * - [graphName] defaults to [FalkorDBGraphOperations.DEFAULT_GRAPH_NAME].
 *
 * ```kotlin
 * fun Application.module(driver: Driver) {
 *     install(GraphPlugin) {
 *         falkorDB(driver, graphName = "social")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.falkorDB(
    driver: Driver,
    graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME,
): GraphPluginConfig = apply {
    graphName.requireNotBlank("graphName")

    val graphOperations = FalkorDBGraphOperations(driver, graphName)
    val graphSuspendOperations = FalkorDBGraphSuspendOperations(driver, graphName)

    configure(
        backendName = "falkorDB",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("FalkorDBGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("FalkorDBGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
        ),
    )
}

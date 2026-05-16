package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

/**
 * Configures [GraphPlugin] to use an in-memory TinkerGraph backend.
 *
 * ## Behavior / Contract
 * - The plugin creates the [TinkerGraphOperations] delegate internally.
 * - [TinkerGraphSuspendOperations] shares the same delegate.
 * - The shared delegate is closed exactly once on application stop.
 *
 * ```kotlin
 * fun Application.module() {
 *     install(GraphPlugin) {
 *         tinkerGraph()
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.tinkerGraph(): GraphPluginConfig = apply {
    val graphOperations = TinkerGraphOperations()
    val graphSuspendOperations = TinkerGraphSuspendOperations(graphOperations)

    configure(
        backendName = "tinkerGraph",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("TinkerGraphOperations") {
                graphOperations.close()
            },
        ),
    )
}

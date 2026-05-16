package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank

/**
 * Configures [GraphPlugin] to use an Apache AGE backend.
 *
 * ## Behavior / Contract
 * - The caller must complete `Database.connect(...)` before calling this helper.
 * - This helper does not own or close the Exposed `Database`, `DataSource`, or connection pool lifecycle.
 * - [graphName] must not be blank; AGE safe-identifier validation is performed by the backend constructor.
 *
 * ```kotlin
 * fun Application.module() {
 *     Database.connect(dataSource)
 *     install(GraphPlugin) {
 *         age(graphName = "social")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.age(
    graphName: String,
): GraphPluginConfig = apply {
    graphName.requireNotBlank("graphName")

    val graphOperations = AgeGraphOperations(graphName)
    val graphSuspendOperations = AgeGraphSuspendOperations(graphName)

    configure(
        backendName = "age",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("AgeGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("AgeGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
        ),
    )
}

package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import java.util.IdentityHashMap

/**
 * Configuration class for [GraphPlugin].
 *
 * ## Behavior / Contract
 * - Exactly one backend must be selected inside the `install(GraphPlugin) { ... }` block.
 * - [operations] wires already-constructed [GraphOperations] / [GraphSuspendOperations] into the Ktor application.
 * - Caller-owned operations are not closed by default. Pass `closeOnStop = true` to close them on
 *   [io.ktor.server.application.ApplicationStopped].
 * - If both operations share an internal delegate, the caller must ensure idempotent close or keep
 *   `closeOnStop = false`.
 *
 * ```kotlin
 * fun Application.module(syncOps: GraphOperations, suspendOps: GraphSuspendOperations) {
 *     install(GraphPlugin) {
 *         operations(syncOps, suspendOps)
 *     }
 * }
 * ```
 */
class GraphPluginConfig {

    internal var graphOperationsFactory: (() -> GraphOperations)? = null
        private set

    internal var graphSuspendOperationsFactory: (() -> GraphSuspendOperations)? = null
        private set

    internal val closeActions: MutableList<GraphPluginCloseAction> = mutableListOf()

    /**
     * Registers an already-constructed graph facade pair as the plugin state.
     *
     * ## Behavior / Contract
     * - Both [graphOperations] and [graphSuspendOperations] are required.
     * - `closeOnStop` defaults to `false`. Keep the default when an external DI container owns the lifecycle.
     * - When `closeOnStop` is `true`, both objects are deduplicated by object identity and closed exactly once.
     *
     * @param graphOperations sync graph facade
     * @param graphSuspendOperations coroutine graph facade
     * @param closeOnStop whether to close the operations on application stop
     * @throws IllegalArgumentException if a backend has already been configured
     */
    fun operations(
        graphOperations: GraphOperations,
        graphSuspendOperations: GraphSuspendOperations,
        closeOnStop: Boolean = false,
    ): GraphPluginConfig = apply {
        configure(
            backendName = "custom",
            graphOperationsFactory = { graphOperations },
            graphSuspendOperationsFactory = { graphSuspendOperations },
            closeActions = if (closeOnStop) {
                closeOnceByIdentity(graphOperations, graphSuspendOperations)
            } else {
                emptyList()
            },
        )
    }

    internal fun configure(
        backendName: String,
        graphOperationsFactory: () -> GraphOperations,
        graphSuspendOperationsFactory: () -> GraphSuspendOperations,
        closeActions: List<GraphPluginCloseAction> = emptyList(),
    ) {
        require(this.graphOperationsFactory == null && this.graphSuspendOperationsFactory == null) {
            "GraphPlugin backend can only be configured once. Already configured backend: $backendName"
        }

        this.graphOperationsFactory = graphOperationsFactory
        this.graphSuspendOperationsFactory = graphSuspendOperationsFactory
        this.closeActions.addAll(closeActions)
    }

    internal fun resolveState(): GraphPluginState {
        val graphOperations = graphOperationsFactory?.invoke()
            ?: throw IllegalArgumentException("A graph backend must be selected before installing GraphPlugin.")
        val graphSuspendOperations = graphSuspendOperationsFactory?.invoke()
            ?: throw IllegalArgumentException("A graph suspend backend must be selected before installing GraphPlugin.")

        return GraphPluginState(
            graphOperations = graphOperations,
            graphSuspendOperations = graphSuspendOperations,
            closeActions = closeActions.toList(),
        )
    }

    private fun closeOnceByIdentity(vararg closeables: AutoCloseable): List<GraphPluginCloseAction> {
        val seen = IdentityHashMap<AutoCloseable, Boolean>()
        return closeables
            .filter { seen.put(it, true) == null }
            .map { closeable ->
                GraphPluginCloseAction(closeable.javaClass.simpleName.ifBlank { "GraphOperations" }) {
                    closeable.close()
                }
            }
    }
}

internal data class GraphPluginCloseAction(
    val name: String,
    val action: () -> Unit,
)

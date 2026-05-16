package io.bluetape4k.graph.ktor

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.util.AttributeKey

/**
 * Entry point for installing the `bluetape4k-graph` facade as a Ktor 3.x application plugin.
 *
 * ## Behavior / Contract
 * - Install with `install(GraphPlugin) { tinkerGraph() }` or `install(GraphPlugin) { operations(sync, suspend) }`.
 * - Throws [IllegalArgumentException] at install time if no backend is selected.
 * - The resolved [GraphPluginState] is stored in [Application.attributes].
 * - On stop, only close actions registered during configuration run; caller-owned drivers and `DataSource`
 *   instances are not closed.
 *
 * ```kotlin
 * fun Application.module() {
 *     install(GraphPlugin) {
 *         tinkerGraph()
 *     }
 *
 *     val graph = graphSuspendOperations()
 * }
 * ```
 */
val GraphPlugin = createApplicationPlugin(
    name = GraphPluginInternals.NAME,
    createConfiguration = ::GraphPluginConfig,
) {
    val state = pluginConfig.resolveState()
    application.attributes.put(GraphPluginStateKey, state)

    on(MonitoringEvent(ApplicationStarted)) { application ->
        GraphPluginInternals.log.info {
            "GraphPlugin started - application=${application.javaClass.simpleName}"
        }
    }

    on(MonitoringEvent(ApplicationStopped)) { application ->
        GraphPluginInternals.log.info {
            "GraphPlugin stopped - application=${application.javaClass.simpleName}"
        }
        state.close()
    }
}

internal val GraphPluginStateKey: AttributeKey<GraphPluginState> =
    AttributeKey("io.bluetape4k.graph.ktor.GraphPluginState")

internal object GraphPluginInternals: KLogging() {
    const val NAME: String = "Graph"
}

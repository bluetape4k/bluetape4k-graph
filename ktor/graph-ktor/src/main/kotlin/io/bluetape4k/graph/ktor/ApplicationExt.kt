package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/**
 * Returns the [GraphPluginState] of the [GraphPlugin] installed in this [Application].
 *
 * ## Behavior / Contract
 * - Throws [IllegalStateException] if [GraphPlugin] has not been installed.
 *
 * @throws IllegalStateException if the plugin is not installed
 */
fun Application.graphPluginState(): GraphPluginState =
    attributes.getOrNull(GraphPluginStateKey)
        ?: error("GraphPlugin is not installed in this Application.")

/**
 * Returns the blocking [GraphOperations] from the current [Application].
 *
 * ## Behavior / Contract
 * - Prefer [graphSuspendOperations] inside Ktor route suspend contexts.
 * - Throws [IllegalStateException] if the plugin is not installed.
 */
fun Application.graphOperations(): GraphOperations =
    graphPluginState().graphOperations

/**
 * Returns the coroutine-first [GraphSuspendOperations] from the current [Application].
 *
 * ## Behavior / Contract
 * - This is the preferred facade for Ktor route handlers and coroutine services.
 * - Throws [IllegalStateException] if the plugin is not installed.
 */
fun Application.graphSuspendOperations(): GraphSuspendOperations =
    graphPluginState().graphSuspendOperations

/**
 * Returns the blocking [GraphOperations] from an [ApplicationCall] in a route handler.
 *
 * ```kotlin
 * routing {
 *     get("/cities/count") {
 *         call.respondText(call.graphOperations().countVertices("City").toString())
 *     }
 * }
 * ```
 */
fun ApplicationCall.graphOperations(): GraphOperations =
    application.graphOperations()

/**
 * Returns the coroutine-first [GraphSuspendOperations] from an [ApplicationCall] in a route handler.
 *
 * ```kotlin
 * routing {
 *     get("/cities/count") {
 *         call.respondText(call.graphSuspendOperations().countVertices("City").toString())
 *     }
 * }
 * ```
 */
fun ApplicationCall.graphSuspendOperations(): GraphSuspendOperations =
    application.graphSuspendOperations()

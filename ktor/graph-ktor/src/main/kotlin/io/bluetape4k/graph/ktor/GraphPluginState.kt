package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn

/**
 * Graph integration state resolved by [GraphPlugin].
 *
 * ## Behavior / Contract
 * - [graphOperations] is the blocking compatibility API.
 * - [graphSuspendOperations] is the preferred coroutine API for Ktor routes and coroutine code.
 * - [close] executes registered close actions independently; a failure in one action does not
 *   prevent the remaining actions from running.
 *
 * ```kotlin
 * val state = application.graphPluginState()
 * val count = state.graphOperations.countVertices("City")
 * ```
 */
class GraphPluginState internal constructor(
    val graphOperations: GraphOperations,
    val graphSuspendOperations: GraphSuspendOperations,
    private val closeActions: List<GraphPluginCloseAction>,
): AutoCloseable {

    override fun close() {
        closeActions.forEach { closeAction ->
            runCatching {
                closeAction.action()
            }.onFailure { e ->
                log.warn(e) { "GraphPlugin close action failed: ${closeAction.name}" }
            }
        }
    }

    private companion object: KLogging()
}

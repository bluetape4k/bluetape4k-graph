package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn

/**
 * [GraphPlugin]이 resolve한 graph integration state입니다.
 *
 * ## 동작/계약
 * - [graphOperations]는 blocking compatibility API입니다.
 * - [graphSuspendOperations]는 Ktor route와 coroutine code에서 우선 사용할 coroutine API입니다.
 * - [close]는 등록된 close action을 독립적으로 실행합니다. 하나의 close 실패가 나머지 close를 막지 않습니다.
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

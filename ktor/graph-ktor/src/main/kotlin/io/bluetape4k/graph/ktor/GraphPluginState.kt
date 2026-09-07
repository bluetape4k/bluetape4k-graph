package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import kotlinx.atomicfu.atomic

/**
 * [GraphPlugin]이 확정한 graph integration state.
 *
 * ## 동작 계약
 * - [graphOperations]는 blocking compatibility API다.
 * - [graphSuspendOperations]는 Ktor route와 coroutine code에서 우선 사용할 coroutine API다.
 * - [close]는 등록된 종료 동작을 독립적으로 실행한다. 한 동작의 실패가 나머지 동작 실행을 막지 않는다.
 * - 성공한 종료 동작은 다시 실행하지 않고, 실패한 동작만 다음 [close]에서 재시도한다.
 * - 동시에 [close]를 호출해도 전체 종료 순회를 하나만 실행해 동작 순서와 중복 방지를 함께 보존한다.
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
    private val closing = atomic(false)

    override fun close() {
        if (closing.compareAndSet(false, true)) {
            try {
                closeGraphPluginActions(closeActions)
            } finally {
                closing.value = false
            }
        }
    }
}

internal fun closeGraphPluginActions(closeActions: List<GraphPluginCloseAction>) {
    closeActions.forEach { closeAction ->
        runCatching {
            closeAction.close()
        }.onFailure { e ->
            GraphPluginCloseLogger.log.warn(e) {
                "GraphPlugin close action failed: ${closeAction.name}"
            }
        }
    }
}

private object GraphPluginCloseLogger: KLogging()

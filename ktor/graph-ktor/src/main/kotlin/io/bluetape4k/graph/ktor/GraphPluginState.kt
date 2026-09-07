package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.ktor.core.ApplicationResourceRegistry
import kotlinx.atomicfu.atomic

/**
 * [GraphPlugin]이 확정한 graph integration state.
 *
 * ## 동작 계약
 * - [graphOperations]는 blocking compatibility API다.
 * - [graphSuspendOperations]는 Ktor route와 coroutine code에서 우선 사용할 coroutine API다.
 * - [close]는 등록된 종료 동작을 독립적으로 실행한다. 한 동작의 실패가 나머지 동작 실행을 막지 않는다.
 * - 성공한 종료 동작은 다시 실행하지 않고, 실패한 동작만 다음 [close]에서 재시도한다.
 * - 동시에 [close]를 호출하면 하나의 종료 순회로 합치고 후속 호출은 기다리지 않고 반환한다.
 * - Plugin 소유 종료 동작은 공통 [ApplicationResourceRegistry]에 하나의 group으로 등록한다.
 *   Application 종료 중 실패하면 registry report에는 group failure가 남고, 원래 예외는 Graph logger에 기록한다.
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
        closeAndCollectFailures()
    }

    private fun closeAndCollectFailures(): GraphPluginCloseOutcome {
        if (closing.compareAndSet(false, true)) {
            try {
                return closeGraphPluginActionsWithOutcome(closeActions)
            } finally {
                closing.value = false
            }
        }
        return GraphPluginCloseOutcome.NONE
    }

    /** Plugin 소유 종료 동작만 하나의 bounded resource group으로 공통 registry에 등록한다. */
    @JvmSynthetic
    internal fun registerCloseActions(registry: ApplicationResourceRegistry) {
        if (closeActions.isNotEmpty()) {
            registry.register {
                val outcome = closeAndCollectFailures()
                when {
                    outcome.fatalFailure -> throw GraphPluginLifecycleFatalCloseFailure()
                    outcome.failureCount > 0 -> throw GraphPluginLifecycleCloseFailure()
                }
            }
        }
    }
}

internal fun closeGraphPluginActions(closeActions: List<GraphPluginCloseAction>) {
    closeGraphPluginActionsWithOutcome(closeActions)
}

private fun closeGraphPluginActionsWithOutcome(
    closeActions: List<GraphPluginCloseAction>,
): GraphPluginCloseOutcome {
    var failureCount = 0
    var fatalFailure = false
    closeActions.forEach { closeAction ->
        runCatching {
            closeAction.close()
        }.onFailure { e ->
            failureCount += 1
            fatalFailure = fatalFailure || e is Error
            GraphPluginCloseLogger.log.warn(e) {
                "GraphPlugin close action failed: ${closeAction.name}"
            }
        }
    }
    return GraphPluginCloseOutcome(failureCount, fatalFailure)
}

private class GraphPluginLifecycleCloseFailure:
    IllegalStateException("GraphPlugin resource close failed.")

private class GraphPluginLifecycleFatalCloseFailure:
    Error("GraphPlugin resource close failed.")

private data class GraphPluginCloseOutcome(
    val failureCount: Int,
    val fatalFailure: Boolean,
) {
    companion object {
        val NONE = GraphPluginCloseOutcome(0, false)
    }
}

private object GraphPluginCloseLogger: KLogging()

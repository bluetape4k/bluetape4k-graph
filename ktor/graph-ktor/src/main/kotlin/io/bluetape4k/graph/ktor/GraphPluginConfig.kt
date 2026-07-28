package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import java.util.IdentityHashMap

/**
 * [GraphPlugin] 설정 class.
 *
 * ## 동작 계약
 * - `install(GraphPlugin) { ... }` block 안에서 정확히 하나의 backend를 선택해야 한다.
 * - [operations]는 이미 생성된 [GraphOperations] / [GraphSuspendOperations]를 Ktor application에 연결한다.
 * - 호출자 소유 operations는 기본적으로 닫지 않는다. [io.ktor.server.application.ApplicationStopped]에서 닫으려면 `closeOnStop = true`를 전달한다.
 * - 두 operations가 내부 delegate를 공유한다면 호출자는 idempotent close를 보장하거나 `closeOnStop = false`를 유지해야 한다.
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
     * 이미 생성된 graph facade 쌍을 plugin state로 등록한다.
     *
     * ## 동작 계약
     * - [graphOperations]와 [graphSuspendOperations]는 모두 필요하다.
     * - `closeOnStop`의 기본값은 `false`다. 외부 DI container가 lifecycle을 소유하면 기본값을 유지한다.
     * - `closeOnStop`이 `true`이면 두 객체를 object identity로 deduplicate한 뒤 정확히 한 번 닫는다.
     *
     * @param graphOperations 동기 graph facade
     * @param graphSuspendOperations 코루틴 graph facade.
     * @param closeOnStop application stop 시 operations를 닫을지 여부
     * @throws IllegalArgumentException backend가 이미 설정된 경우.
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

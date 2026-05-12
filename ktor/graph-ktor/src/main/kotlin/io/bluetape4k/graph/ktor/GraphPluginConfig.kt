package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import java.util.IdentityHashMap

/**
 * [GraphPlugin] 설정 클래스입니다.
 *
 * ## 동작/계약
 * - `install(GraphPlugin) { ... }` 블록에서 backend를 반드시 한 번 선택해야 합니다.
 * - [operations]는 이미 생성된 [GraphOperations] / [GraphSuspendOperations]를 Ktor application에 연결합니다.
 * - 기본적으로 caller-owned operations를 닫지 않습니다. `closeOnStop`을 `true`로 지정한 경우에만
 *   [io.ktor.server.application.ApplicationStopped] 시점에 전달된 operations를 닫습니다.
 * - 두 operations가 내부 delegate를 공유한다면 caller가 idempotent close를 보장하거나 `closeOnStop`을 꺼야 합니다.
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
     * 이미 구성된 graph facade pair를 plugin state로 등록합니다.
     *
     * ## 동작/계약
     * - [graphOperations]와 [graphSuspendOperations]는 모두 필수입니다.
     * - `closeOnStop` 기본값은 `false`입니다. 외부 DI/container가 lifecycle을 소유하는 경우 기본값을 유지합니다.
     * - `closeOnStop`이 `true`이면 두 객체를 object identity 기준으로 중복 제거한 뒤 한 번씩 닫습니다.
     *
     * @param graphOperations 동기 graph facade
     * @param graphSuspendOperations 코루틴 graph facade
     * @param closeOnStop application stop 시 operations를 닫을지 여부
     * @throws IllegalArgumentException backend가 이미 설정된 경우
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
            "GraphPlugin backend는 한 번만 설정할 수 있습니다. 이미 설정된 backend를 확인하세요: $backendName"
        }

        this.graphOperationsFactory = graphOperationsFactory
        this.graphSuspendOperationsFactory = graphSuspendOperationsFactory
        this.closeActions.addAll(closeActions)
    }

    internal fun resolveState(): GraphPluginState {
        val graphOperations = graphOperationsFactory?.invoke()
            ?: throw IllegalArgumentException("GraphPlugin 설치 전 graph backend를 명시적으로 선택해야 합니다.")
        val graphSuspendOperations = graphSuspendOperationsFactory?.invoke()
            ?: throw IllegalArgumentException("GraphPlugin 설치 전 graph suspend backend를 명시적으로 선택해야 합니다.")

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

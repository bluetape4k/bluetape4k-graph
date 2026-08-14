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
        if (this.graphOperationsFactory != null || this.graphSuspendOperationsFactory != null) {
            closeGraphPluginActions(closeActions)
            throw duplicateBackendException(backendName)
        }

        this.graphOperationsFactory = graphOperationsFactory
        this.graphSuspendOperationsFactory = graphSuspendOperationsFactory
        this.closeActions.addAll(closeActions)
    }

    /**
     * Managed backend가 resource를 만들기 전에 중복 구성을 차단한다.
     */
    internal fun ensureBackendAvailable(backendName: String) {
        if (this.graphOperationsFactory != null || this.graphSuspendOperationsFactory != null) {
            throw duplicateBackendException(backendName)
        }
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

private fun duplicateBackendException(backendName: String): IllegalArgumentException =
    IllegalArgumentException(
        "GraphPlugin backend can only be configured once. Already configured backend: $backendName",
    )

internal class GraphPluginCloseAction(
    val name: String,
    private val action: () -> Unit,
) {
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    fun close() {
        if (closed.compareAndSet(false, true)) {
            action()
        }
    }
}

/**
 * 생성된 resource와 plugin 종료 시 사용할 close action을 함께 보관한다.
 */
internal class ManagedGraphPluginResource<T : AutoCloseable>(
    val value: T,
    val closeAction: GraphPluginCloseAction,
)

/**
 * Managed backend 생성 중 획득한 자원의 소유권과 rollback 순서를 추적한다.
 */
internal class ManagedGraphPluginResources {
    private val resources = mutableListOf<GraphPluginCloseAction>()
    private var committed = false

    fun <T : AutoCloseable> own(name: String, resource: T): ManagedGraphPluginResource<T> {
        val closeAction = register(name) { resource.close() }
        return ManagedGraphPluginResource(resource, closeAction)
    }

    fun register(name: String, action: () -> Unit): GraphPluginCloseAction {
        val closeAction = GraphPluginCloseAction(name, action)
        resources += closeAction
        return closeAction
    }

    fun commit() {
        committed = true
    }

    fun rollback() {
        if (!committed) {
            committed = true
            closeGraphPluginActions(resources.asReversed())
        }
    }
}

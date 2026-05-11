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
 * Ktor 3.x application에서 `bluetape4k-graph` facade를 설치하는 plugin 진입점입니다.
 *
 * ## 동작/계약
 * - `install(GraphPlugin) { tinkerGraph() }` 또는 `install(GraphPlugin) { operations(sync, suspend) }`로 설치합니다.
 * - backend가 명시적으로 선택되지 않으면 install 시점에 [IllegalArgumentException]이 발생합니다.
 * - resolve된 [GraphPluginState]는 [Application.attributes]에 저장됩니다.
 * - stop 시점에는 설정에 등록된 close action만 실행합니다. caller-owned driver나 `DataSource`는 닫지 않습니다.
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
            "GraphPlugin 시작 - application=${application.javaClass.simpleName}"
        }
    }

    on(MonitoringEvent(ApplicationStopped)) { application ->
        GraphPluginInternals.log.info {
            "GraphPlugin 종료 - application=${application.javaClass.simpleName}"
        }
        state.close()
    }
}

internal val GraphPluginStateKey: AttributeKey<GraphPluginState> =
    AttributeKey("io.bluetape4k.graph.ktor.GraphPluginState")

internal object GraphPluginInternals: KLogging() {
    const val NAME: String = "Graph"
}

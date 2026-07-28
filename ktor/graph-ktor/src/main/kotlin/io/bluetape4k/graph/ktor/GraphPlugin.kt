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
 * `bluetape4k-graph` facade를 Ktor 3.x application plugin으로 설치하는 진입점.
 *
 * ## 동작 계약
 * - `install(GraphPlugin) { tinkerGraph() }` 또는 `install(GraphPlugin) { operations(sync, suspend) }`로 설치한다.
 * - backend가 선택되지 않으면 설치 시점에 [IllegalArgumentException]을 던진다.
 * - 확정된 [GraphPluginState]는 [Application.attributes]에 저장된다.
 * - stop 시 설정 중 등록된 종료 동작만 실행한다. 호출자 소유 driver와 `DataSource` instance는 닫지 않는다.
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

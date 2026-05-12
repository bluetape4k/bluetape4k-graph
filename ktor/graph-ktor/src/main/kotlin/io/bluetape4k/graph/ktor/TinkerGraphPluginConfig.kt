package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

/**
 * Ktor [GraphPlugin]을 in-memory TinkerGraph backend로 설정합니다.
 *
 * ## 동작/계약
 * - plugin이 [TinkerGraphOperations] delegate를 직접 생성합니다.
 * - [TinkerGraphSuspendOperations]는 같은 delegate를 공유합니다.
 * - application stop 시 shared delegate를 정확히 한 번만 닫습니다.
 *
 * ```kotlin
 * fun Application.module() {
 *     install(GraphPlugin) {
 *         tinkerGraph()
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.tinkerGraph(): GraphPluginConfig = apply {
    val graphOperations = TinkerGraphOperations()
    val graphSuspendOperations = TinkerGraphSuspendOperations(graphOperations)

    configure(
        backendName = "tinkerGraph",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("TinkerGraphOperations") {
                graphOperations.close()
            },
        ),
    )
}

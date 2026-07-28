package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations

/**
 * [GraphPlugin]이 in-memory TinkerGraph backend를 사용하도록 설정한다.
 *
 * ## 동작 계약
 * - Plugin은 [TinkerGraphOperations] delegate를 내부에서 생성한다.
 * - [TinkerGraphSuspendOperations]는 같은 delegate를 공유한다.
 * - 공유 delegate는 application stop 시 정확히 한 번 닫힌다.
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

package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank

/**
 * Ktor [GraphPlugin]을 Apache AGE backend로 설정합니다.
 *
 * ## 동작/계약
 * - 호출 전에 caller가 Exposed `Database.connect(...)`를 완료해야 합니다.
 * - 이 helper는 Exposed `Database`, `DataSource`, connection pool lifecycle을 소유하거나 닫지 않습니다.
 * - [graphName]은 blank가 아니어야 하며 AGE safe identifier 검증은 backend constructor가 수행합니다.
 *
 * ```kotlin
 * fun Application.module() {
 *     Database.connect(dataSource)
 *     install(GraphPlugin) {
 *         age(graphName = "social")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.age(
    graphName: String,
): GraphPluginConfig = apply {
    graphName.requireNotBlank("graphName")

    val graphOperations = AgeGraphOperations(graphName)
    val graphSuspendOperations = AgeGraphSuspendOperations(graphName)

    configure(
        backendName = "age",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("AgeGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("AgeGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
        ),
    )
}

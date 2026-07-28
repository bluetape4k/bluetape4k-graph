package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank

/**
 * [GraphPlugin]이 Apache AGE backend를 사용하도록 설정한다.
 *
 * ## 동작 계약
 * - 호출자는 이 helper를 호출하기 전에 `Database.connect(...)`를 완료해야 한다.
 * - 이 helper는 Exposed `Database`, `DataSource`, connection pool lifecycle을 소유하거나 닫지 않는다.
 * - [graphName]은 빈 문자열이면 안 된다. AGE safe-identifier 검증은 backend 생성자가 수행한다.
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
    )
}

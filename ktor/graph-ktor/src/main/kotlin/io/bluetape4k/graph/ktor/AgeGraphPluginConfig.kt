package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * [GraphPlugin]이 Apache AGE backend를 사용하도록 설정한다.
 *
 * ## 동작 계약
 * - 호출자가 소유한 [database]를 operations 양쪽에 명시적으로 전달한다.
 * - 이 helper는 Exposed `Database`, `DataSource`, connection pool lifecycle을 소유하거나 닫지 않는다.
 * - [graphName]은 빈 문자열이면 안 된다. AGE safe-identifier 검증은 backend 생성자가 수행한다.
 *
 * ```kotlin
 * fun Application.module() {
 *     install(GraphPlugin) {
 *         age(database, graphName = "social")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.age(
    database: Database,
    graphName: String,
): GraphPluginConfig = apply {
    graphName.requireNotBlank("graphName")

    val graphOperations = AgeGraphOperations(database, graphName)
    val graphSuspendOperations = AgeGraphSuspendOperations(database, graphName)

    configure(
        backendName = "age",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
    )
}

/**
 * 전역 Exposed Database를 조회하는 기존 AGE helper다.
 *
 * 새 코드는 명시적인 [Database]를 받는 [age] 오버로드를 사용해야 한다.
 */
@Deprecated("명시적인 Database를 전달하는 age(database, graphName)를 사용하세요.")
@Suppress("DEPRECATION")
fun GraphPluginConfig.age(
    graphName: String,
): GraphPluginConfig {
    graphName.requireNotBlank("graphName")
    configure(
        backendName = "age",
        graphOperationsFactory = { AgeGraphOperations(graphName) },
        graphSuspendOperationsFactory = { AgeGraphSuspendOperations(graphName) },
    )
    return this
}

package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver

/**
 * Ktor [GraphPlugin]을 Memgraph backend로 설정합니다.
 *
 * ## 동작/계약
 * - [driver]는 caller-owned resource입니다. 이 helper는 driver를 닫지 않습니다.
 * - [database] 기본값은 `MemgraphGraphOperations`와 Spring Boot properties가 사용하는 `"memgraph"`입니다.
 *
 * ```kotlin
 * fun Application.module(driver: Driver) {
 *     install(GraphPlugin) {
 *         memgraph(driver)
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.memgraph(
    driver: Driver,
    database: String = "memgraph",
): GraphPluginConfig = apply {
    database.requireNotBlank("database")

    val graphOperations = MemgraphGraphOperations(driver, database)
    val graphSuspendOperations = MemgraphGraphSuspendOperations(driver, database)

    configure(
        backendName = "memgraph",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("MemgraphGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("MemgraphGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
        ),
    )
}

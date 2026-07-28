package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver

/**
 * [GraphPlugin]이 Memgraph backend를 사용하도록 설정한다.
 *
 * ## 동작 계약
 * - [driver]는 호출자 소유 resource이며, 이 helper는 닫지 않는다.
 * - [database]의 기본값은 `"memgraph"`이며, `MemgraphGraphOperations`와 Spring Boot properties의 기본값과 일치한다.
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
    )
}

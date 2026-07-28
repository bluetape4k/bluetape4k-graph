package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver

/**
 * [GraphPlugin]이 Neo4j backend를 사용하도록 설정한다.
 *
 * ## 동작 계약
 * - [driver]는 호출자 소유 resource이며, 이 helper는 닫지 않는다.
 * - [database]는 blank이면 안 된다.
 *
 * ```kotlin
 * fun Application.module(driver: Driver) {
 *     install(GraphPlugin) {
 *         neo4j(driver, database = "neo4j")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.neo4j(
    driver: Driver,
    database: String = "neo4j",
): GraphPluginConfig = apply {
    database.requireNotBlank("database")

    val graphOperations = Neo4jGraphOperations(driver, database)
    val graphSuspendOperations = Neo4jGraphSuspendOperations(driver, database)

    configure(
        backendName = "neo4j",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
    )
}

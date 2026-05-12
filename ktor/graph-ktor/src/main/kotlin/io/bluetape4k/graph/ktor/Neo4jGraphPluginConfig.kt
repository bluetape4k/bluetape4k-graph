package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Driver

/**
 * Ktor [GraphPlugin]을 Neo4j backend로 설정합니다.
 *
 * ## 동작/계약
 * - [driver]는 caller-owned resource입니다. 이 helper는 driver를 닫지 않습니다.
 * - [database]는 blank가 아니어야 합니다.
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
        closeActions = listOf(
            GraphPluginCloseAction("Neo4jGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("Neo4jGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
        ),
    )
}

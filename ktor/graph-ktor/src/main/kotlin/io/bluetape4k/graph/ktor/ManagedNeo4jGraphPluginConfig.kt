package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

/**
 * [GraphPlugin]이 소유하는 Neo4j driver를 생성하는 Ktor DSL.
 *
 * ## 동작 계약
 * - [uri] and [database]는 blank이면 안 된다.
 * - Blank [username]은 `AuthTokens.none()`으로 driver를 생성한다.
 * - 이 DSL이 만든 driver는 plugin 소유이며 `ApplicationStopped`에서 닫힌다.
 * - 기존 `neo4j(driver, database)` helper는 호출자 소유 driver 계약을 유지한다.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     neo4j {
 *         uri = "bolt://localhost:7687"
 *         username = "neo4j"
 *         password = "secret"
 *     }
 * }
 * ```
 */
class ManagedNeo4jGraphPluginConfig {
    var uri: String = "bolt://localhost:7687"
    var username: String = ""
    var password: String = ""
    var database: String = "neo4j"
}

/**
 * [GraphPlugin]을 plugin 소유 Neo4j driver로 설정한다.
 */
fun GraphPluginConfig.neo4j(
    configure: ManagedNeo4jGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedNeo4jGraphPluginConfig().apply(configure)
    props.uri.requireNotBlank("uri")
    props.database.requireNotBlank("database")

    val authToken = if (props.username.isBlank()) {
        AuthTokens.none()
    } else {
        AuthTokens.basic(props.username, props.password)
    }
    val driver = GraphDatabase.driver(props.uri, authToken)
    val graphOperations = Neo4jGraphOperations(driver, props.database)
    val graphSuspendOperations = Neo4jGraphSuspendOperations(driver, props.database)

    configure(
        backendName = "managedNeo4j",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
        closeActions = listOf(
            GraphPluginCloseAction("Neo4jGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("Neo4jGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
            GraphPluginCloseAction("Neo4jDriver") {
                driver.close()
            },
        ),
    )
}

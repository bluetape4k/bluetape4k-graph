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
@Suppress("TooGenericExceptionCaught")
fun GraphPluginConfig.neo4j(
    configure: ManagedNeo4jGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedNeo4jGraphPluginConfig().apply(configure)
    props.uri.requireNotBlank("uri")
    props.database.requireNotBlank("database")
    ensureBackendAvailable("managedNeo4j")

    val authToken = if (props.username.isBlank()) {
        AuthTokens.none()
    } else {
        AuthTokens.basic(props.username, props.password)
    }
    val resources = ManagedGraphPluginResources()
    try {
        val driver = resources.own("Neo4jDriver", GraphDatabase.driver(props.uri, authToken))
        val graphOperations = resources.own(
            "Neo4jGraphOperations",
            Neo4jGraphOperations(driver.value, props.database),
        )
        val graphSuspendOperations = resources.own(
            "Neo4jGraphSuspendOperations",
            Neo4jGraphSuspendOperations(driver.value, props.database),
        )

        configure(
            backendName = "managedNeo4j",
            graphOperationsFactory = { graphOperations.value },
            graphSuspendOperationsFactory = { graphSuspendOperations.value },
            closeActions = listOf(
                graphOperations.closeAction,
                graphSuspendOperations.closeAction,
                driver.closeAction,
            ),
        )
        resources.commit()
    } catch (e: Exception) {
        resources.rollback()
        throw e
    }
}

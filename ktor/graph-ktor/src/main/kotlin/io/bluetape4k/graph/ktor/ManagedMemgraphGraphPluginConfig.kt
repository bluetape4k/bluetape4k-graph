package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

/**
 * [GraphPlugin]이 소유하는 Memgraph driver를 생성하는 Ktor DSL.
 *
 * ## 동작 계약
 * - Memgraph는 Neo4j Bolt 호환 Java driver를 사용한다.
 * - [uri] and [database]는 blank이면 안 된다.
 * - Blank [username]은 인증 없는 driver를 생성한다.
 * - 이 DSL이 만든 driver는 plugin 소유이며 `ApplicationStopped`에서 닫힌다.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     memgraph {
 *         uri = "bolt://localhost:7687"
 *         database = "memgraph"
 *     }
 * }
 * ```
 */
class ManagedMemgraphGraphPluginConfig {
    var uri: String = "bolt://localhost:7687"
    var username: String = ""
    var password: String = ""
    var database: String = "memgraph"
}

/**
 * [GraphPlugin]을 plugin 소유 Memgraph driver로 설정한다.
 */
@Suppress("TooGenericExceptionCaught")
fun GraphPluginConfig.memgraph(
    configure: ManagedMemgraphGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedMemgraphGraphPluginConfig().apply(configure)
    props.uri.requireNotBlank("uri")
    props.database.requireNotBlank("database")
    ensureBackendAvailable("managedMemgraph")

    val authToken = if (props.username.isBlank()) {
        AuthTokens.none()
    } else {
        AuthTokens.basic(props.username, props.password)
    }
    val resources = ManagedGraphPluginResources()
    try {
        val driver = resources.own("MemgraphDriver", GraphDatabase.driver(props.uri, authToken))
        val graphOperations = resources.own(
            "MemgraphGraphOperations",
            MemgraphGraphOperations(driver.value, props.database),
        )
        val graphSuspendOperations = resources.own(
            "MemgraphGraphSuspendOperations",
            MemgraphGraphSuspendOperations(driver.value, props.database),
        )

        configure(
            backendName = "managedMemgraph",
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

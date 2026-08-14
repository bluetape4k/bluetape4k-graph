package io.bluetape4k.graph.ktor

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber

/**
 * [GraphPlugin]이 소유하는 FalkorDB driver를 생성하는 Ktor DSL.
 *
 * ## 동작 계약
 * - [host]와 [graphName]은 blank이면 안 된다.
 * - [port]는 양수여야 한다.
 * - Blank [username]은 인증 없는 `FalkorDB.driver(host, port)`를 사용한다.
 * - 이 DSL이 만든 driver는 plugin 소유이며 `ApplicationStopped`에서 닫힌다.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     falkorDB {
 *         host = "localhost"
 *         port = 6379
 *         graphName = "social"
 *     }
 * }
 * ```
 */
class ManagedFalkorDBGraphPluginConfig {
    var host: String = "localhost"
    var port: Int = 6379
    var username: String = ""
    var password: String = ""
    var graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME
}

/**
 * [GraphPlugin]을 plugin 소유 FalkorDB driver로 설정한다.
 */
@Suppress("TooGenericExceptionCaught")
fun GraphPluginConfig.falkorDB(
    configure: ManagedFalkorDBGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedFalkorDBGraphPluginConfig().apply(configure)
    props.host.requireNotBlank("host")
    props.port.requirePositiveNumber("port")
    props.graphName.requireNotBlank("graphName")
    ensureBackendAvailable("managedFalkorDB")

    val resources = ManagedGraphPluginResources()
    try {
        val driver = resources.own(
            "FalkorDBDriver",
            if (props.username.isBlank()) {
                FalkorDB.driver(props.host, props.port)
            } else {
                FalkorDB.driver(props.host, props.port, props.username, props.password)
            },
        )
        val graphOperations = resources.own(
            "FalkorDBGraphOperations",
            FalkorDBGraphOperations(driver.value, props.graphName),
        )
        val graphSuspendOperations = resources.own(
            "FalkorDBGraphSuspendOperations",
            FalkorDBGraphSuspendOperations(driver.value, props.graphName),
        )

        configure(
            backendName = "managedFalkorDB",
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

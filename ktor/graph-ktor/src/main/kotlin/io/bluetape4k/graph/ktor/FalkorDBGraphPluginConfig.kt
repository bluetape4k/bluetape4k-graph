package io.bluetape4k.graph.ktor

import com.falkordb.Driver
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank

/**
 * [GraphPlugin]이 FalkorDB backend를 사용하도록 설정한다.
 *
 * ## 동작 계약
 * - [driver]는 호출자 소유 resource이며, 이 helper는 닫지 않는다.
 * - [graphName]의 기본값은 [FalkorDBGraphOperations.DEFAULT_GRAPH_NAME]이다.
 *
 * ```kotlin
 * fun Application.module(driver: Driver) {
 *     install(GraphPlugin) {
 *         falkorDB(driver, graphName = "social")
 *     }
 * }
 * ```
 */
fun GraphPluginConfig.falkorDB(
    driver: Driver,
    graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME,
): GraphPluginConfig = apply {
    graphName.requireNotBlank("graphName")

    val graphOperations = FalkorDBGraphOperations(driver, graphName)
    val graphSuspendOperations = FalkorDBGraphSuspendOperations(driver, graphName)

    configure(
        backendName = "falkorDB",
        graphOperationsFactory = { graphOperations },
        graphSuspendOperationsFactory = { graphSuspendOperations },
    )
}

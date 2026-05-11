package io.bluetape4k.graph.ktor

import com.falkordb.Driver
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.support.requireNotBlank

/**
 * Ktor [GraphPlugin]을 FalkorDB backend로 설정합니다.
 *
 * ## 동작/계약
 * - [driver]는 caller-owned resource입니다. 이 helper는 driver를 닫지 않습니다.
 * - [graphName] 기본값은 [FalkorDBGraphOperations.DEFAULT_GRAPH_NAME]입니다.
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
        closeActions = listOf(
            GraphPluginCloseAction("FalkorDBGraphOperations") {
                graphOperations.close()
            },
            GraphPluginCloseAction("FalkorDBGraphSuspendOperations") {
                graphSuspendOperations.close()
            },
        ),
    )
}

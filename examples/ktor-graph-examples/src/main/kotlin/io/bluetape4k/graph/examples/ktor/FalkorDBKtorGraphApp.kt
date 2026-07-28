package io.bluetape4k.graph.examples.ktor

import com.falkordb.Driver
import io.bluetape4k.graph.ktor.GraphPlugin
import io.bluetape4k.graph.ktor.falkorDB
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing

/**
 * FalkorDB backend를 사용하는 Ktor application module이다.
 *
 * ## 동작/계약
 * - [driver]는 caller가 소유한 resource이며, 이 module은 이를 close하지 않는다.
 * - Health/readiness와 JSON을 위한 shared bluetape4k Ktor core default를 설치한다.
 * - [DEMO_GRAPH_NAME]에 bind된 FalkorDB backend로 [GraphPlugin]을 설치한다.
 * - [module]과 같은 demo route(health, readiness, reset, city count, city path)를 노출한다.
 * - Graph name은 [DEMO_GRAPH_NAME]으로 고정되어 [graphDemoRoutes]의 reset 및 query operation이
 *   항상 같은 graph를 대상으로 삼는다.
 *
 * ```kotlin
 * val driver = FalkorDB.driver("localhost", 6379)
 * embeddedServer(CIO, port = 8080) {
 *     falkorDbModule(driver)
 * }.start(wait = true)
 * // Shutdown 시 caller가 driver.close()를 책임진다.
 * ```
 */
fun Application.falkorDbModule(driver: Driver) {
    installGraphExampleKtorCore()
    install(GraphPlugin) {
        falkorDB(driver, graphName = DEMO_GRAPH_NAME)
    }
    routing {
        graphDemoRoutes()
    }
}

/**
 * [graphDemoRoutes] operation과 맞춰 쓰는 graph name이다.
 *
 * FalkorDB operations instance가 [graphDemoRoutes]에서 query하는 것과 같은 Redis key에 vertex와 edge를 쓰도록
 * 반드시 `DemoCityGraph.GRAPH_NAME`과 같아야 한다.
 */
internal const val DEMO_GRAPH_NAME: String = "demo"

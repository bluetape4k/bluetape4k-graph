package io.bluetape4k.graph.examples.ktor

import com.falkordb.Driver
import io.bluetape4k.graph.ktor.GraphPlugin
import io.bluetape4k.graph.ktor.falkorDB
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing

/**
 * Ktor application module using a FalkorDB backend.
 *
 * ## Behavior / Contract
 * - [driver] is a caller-owned resource; this module does not close it.
 * - Installs shared bluetape4k Ktor core defaults for health/readiness and JSON.
 * - Installs [GraphPlugin] with the FalkorDB backend, bound to [DEMO_GRAPH_NAME].
 * - Exposes the same demo routes as [module] (health, readiness, reset, city count, city path).
 * - The graph name is fixed to [DEMO_GRAPH_NAME] so that [graphDemoRoutes] reset
 *   and query operations always target the same graph.
 *
 * ```kotlin
 * val driver = FalkorDB.driver("localhost", 6379)
 * embeddedServer(CIO, port = 8080) {
 *     falkorDbModule(driver)
 * }.start(wait = true)
 * // Caller is responsible for driver.close() on shutdown.
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
 * The graph name matched by [graphDemoRoutes] operations.
 *
 * Must equal `DemoCityGraph.GRAPH_NAME` so that the FalkorDB operations instance
 * writes vertices and edges to the same Redis key that [graphDemoRoutes] queries.
 */
internal const val DEMO_GRAPH_NAME: String = "demo"

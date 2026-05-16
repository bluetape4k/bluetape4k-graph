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
 * - Installs [GraphPlugin] with the FalkorDB backend, binding to the [graphName] graph.
 * - Exposes the same demo routes as [module] (health, reset, city count, city path).
 *
 * ```kotlin
 * val driver = FalkorDB.driver("localhost", 6379)
 * embeddedServer(CIO, port = 8080) {
 *     falkorDbModule(driver)
 * }.start(wait = true)
 * // Caller is responsible for driver.close() on shutdown.
 * ```
 */
fun Application.falkorDbModule(
    driver: Driver,
    graphName: String = DEMO_GRAPH_NAME,
) {
    install(GraphPlugin) {
        falkorDB(driver, graphName = graphName)
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

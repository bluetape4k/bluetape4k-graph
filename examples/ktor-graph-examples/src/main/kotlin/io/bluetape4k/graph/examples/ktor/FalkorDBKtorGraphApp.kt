package io.bluetape4k.graph.examples.ktor

import com.falkordb.FalkorDB
import io.bluetape4k.graph.ktor.GraphPlugin
import io.bluetape4k.graph.ktor.falkorDB
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing

/**
 * Ktor application module using a FalkorDB backend.
 *
 * ## Behavior / Contract
 * - Installs [GraphPlugin] with the FalkorDB backend, binding to the `"demo"` graph
 *   that [graphDemoRoutes] operates on.
 * - Exposes the same demo routes as [module] (health, reset, city count, city path).
 * - The [host] and [port] are resolved from the caller (typically a Testcontainers
 *   singleton in tests, or environment configuration in production).
 *
 * ```kotlin
 * embeddedServer(CIO, port = 8080) {
 *     falkorDbModule(host = "localhost", port = 6379)
 * }.start(wait = true)
 * ```
 */
fun Application.falkorDbModule(host: String, port: Int) {
    val driver = FalkorDB.driver(host, port)
    install(GraphPlugin) {
        falkorDB(driver, graphName = DEMO_GRAPH_NAME)
    }
    routing {
        graphDemoRoutes()
    }
}

/**
 * The graph name used by [falkorDbModule] and matched by [graphDemoRoutes].
 *
 * Must equal `DemoCityGraph.GRAPH_NAME` so that the FalkorDB operations instance
 * writes vertices and edges to the same Redis key that [graphDemoRoutes] queries.
 */
internal const val DEMO_GRAPH_NAME: String = "demo"

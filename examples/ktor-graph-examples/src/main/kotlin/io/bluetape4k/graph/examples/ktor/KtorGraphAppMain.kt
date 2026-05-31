package io.bluetape4k.graph.examples.ktor

import io.bluetape4k.graph.ktor.GraphPlugin
import io.bluetape4k.graph.ktor.graphOperations
import io.bluetape4k.graph.ktor.graphSuspendOperations
import io.bluetape4k.graph.ktor.tinkerGraph
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.ktor.core.Bluetape4kKtorCoreConfig
import io.bluetape4k.ktor.core.installBluetape4kKtorCore
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.toList

/**
 * `examples/ktor-graph-examples`의 executable Ktor application 진입점입니다.
 *
 * ## 동작/계약
 * - 환경 변수 `PORT`로 listen port를 지정하고, 미설정 시 8080을 사용합니다.
 * - 외부 graph database 없이 in-memory TinkerGraph backend를 사용합니다.
 * - `/demo/reset`, `/cities/count`, `/cities/path`, `/health`, `/readyz` route로 plugin 사용법을 보여줍니다.
 */
object KtorGraphAppMain: KLogging() {
    const val ENV_PORT: String = "PORT"
    const val DEFAULT_PORT: Int = 8080

    @JvmStatic
    fun main(args: Array<String>) {
        val port = System.getenv(ENV_PORT)?.toIntOrNull() ?: DEFAULT_PORT
        require(port in 1..65535) {
            "$ENV_PORT must be in 1..65535, but was $port"
        }
        log.info { "KtorGraphAppMain 시작 - port=$port" }

        embeddedServer(CIO, port = port) {
            module()
        }.start(wait = true)
    }
}

/**
 * Ktor graph example application module입니다.
 *
 * ## 동작/계약
 * - [GraphPlugin]을 `tinkerGraph()` backend로 설치합니다.
 * - route handler는 `call.graphOperations()` / `call.graphSuspendOperations()`로 facade를 조회합니다.
 */
fun Application.module() {
    installGraphExampleKtorCore()
    install(GraphPlugin) {
        tinkerGraph()
    }

    routing {
        graphDemoRoutes()
    }
}

internal fun Application.installGraphExampleKtorCore() {
    installBluetape4kKtorCore(
        Bluetape4kKtorCoreConfig(
            healthPath = "/health",
            readinessPath = "/readyz",
        )
    )
}

/**
 * TinkerGraph 기반 demo route를 등록합니다.
 *
 * ## 동작/계약
 * - `POST /demo/reset`: demo city graph를 초기화합니다.
 * - `GET /cities/count`: `City` vertex count를 반환합니다.
 * - `GET /cities/path`: Seoul에서 Busan까지 shortest path를 반환합니다.
 */
fun io.ktor.server.routing.Route.graphDemoRoutes() {
    post("/demo/reset") {
        DemoCityGraph.reset(call.graphOperations())
        call.respondText("reset")
    }

    get("/cities/count") {
        call.respondText(call.graphOperations().countVertices(DemoCityGraph.CITY_LABEL).toString())
    }

    get("/cities/path") {
        val path = DemoCityGraph.shortestPath(call.graphSuspendOperations())
        val response = path?.steps
            ?.filterIsInstance<PathStep.VertexStep>()
            ?.joinToString(" -> ") { it.vertex.properties["name"].toString() }
            ?: "not-found"
        call.respondText(response)
    }
}

private object DemoCityGraph {
    const val GRAPH_NAME: String = "demo"
    const val CITY_LABEL: String = "City"
    const val ROUTE_LABEL: String = "ROUTE_TO"

    fun reset(ops: GraphOperations) {
        ops.dropGraph(GRAPH_NAME)
        ops.createGraph(GRAPH_NAME)

        val seoul = ops.createVertex(CITY_LABEL, mapOf("name" to "Seoul"))
        val daejeon = ops.createVertex(CITY_LABEL, mapOf("name" to "Daejeon"))
        val busan = ops.createVertex(CITY_LABEL, mapOf("name" to "Busan"))

        ops.createEdge(seoul.id, daejeon.id, ROUTE_LABEL, mapOf("distance" to 160.0))
        ops.createEdge(daejeon.id, busan.id, ROUTE_LABEL, mapOf("distance" to 220.0))
    }

    suspend fun shortestPath(ops: GraphSuspendOperations): io.bluetape4k.graph.model.GraphPath? {
        val cities = ops.findVerticesByLabel(CITY_LABEL).toList()
        val seoul = cities.firstOrNull { it.properties["name"] == "Seoul" } ?: return null
        val busan = cities.firstOrNull { it.properties["name"] == "Busan" } ?: return null
        return ops.shortestPath(seoul.id, busan.id, PathOptions(edgeLabel = ROUTE_LABEL))
    }
}

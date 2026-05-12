package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/**
 * 현재 [Application]에 설치된 [GraphPlugin]의 [GraphPluginState]를 조회합니다.
 *
 * ## 동작/계약
 * - [GraphPlugin]이 설치되지 않은 경우 [IllegalStateException]을 던집니다.
 *
 * @throws IllegalStateException plugin 미설치 시
 */
fun Application.graphPluginState(): GraphPluginState =
    attributes.getOrNull(GraphPluginStateKey)
        ?: error("GraphPlugin이 Application에 설치되지 않았습니다.")

/**
 * 현재 [Application]에서 blocking [GraphOperations]를 조회합니다.
 *
 * ## 동작/계약
 * - Ktor route의 suspend context에서는 [graphSuspendOperations]를 우선 사용하세요.
 * - plugin 미설치 시 [IllegalStateException]을 던집니다.
 */
fun Application.graphOperations(): GraphOperations =
    graphPluginState().graphOperations

/**
 * 현재 [Application]에서 coroutine-first [GraphSuspendOperations]를 조회합니다.
 *
 * ## 동작/계약
 * - Ktor route handler와 coroutine service에서 우선 사용할 facade입니다.
 * - plugin 미설치 시 [IllegalStateException]을 던집니다.
 */
fun Application.graphSuspendOperations(): GraphSuspendOperations =
    graphPluginState().graphSuspendOperations

/**
 * route handler의 [ApplicationCall]에서 blocking [GraphOperations]를 조회합니다.
 *
 * ```kotlin
 * routing {
 *     get("/cities/count") {
 *         call.respondText(call.graphOperations().countVertices("City").toString())
 *     }
 * }
 * ```
 */
fun ApplicationCall.graphOperations(): GraphOperations =
    application.graphOperations()

/**
 * route handler의 [ApplicationCall]에서 coroutine-first [GraphSuspendOperations]를 조회합니다.
 *
 * ```kotlin
 * routing {
 *     get("/cities/count") {
 *         call.respondText(call.graphSuspendOperations().countVertices("City").toString())
 *     }
 * }
 * ```
 */
fun ApplicationCall.graphSuspendOperations(): GraphSuspendOperations =
    application.graphSuspendOperations()

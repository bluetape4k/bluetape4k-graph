package io.bluetape4k.graph.ktor

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/**
 * 이 [Application]에 설치된 [GraphPlugin]의 [GraphPluginState]를 반환한다.
 *
 * ## 동작 계약
 * - [GraphPlugin]이 설치되지 않았으면 [IllegalStateException]을 던진다.
 *
 * @throws IllegalStateException plugin이 설치되지 않은 경우.
 */
fun Application.graphPluginState(): GraphPluginState =
    attributes.getOrNull(GraphPluginStateKey)
        ?: error("GraphPlugin is not installed in this Application.")

/**
 * 현재 [Application]에서 blocking [GraphOperations]를 반환한다.
 *
 * ## 동작 계약
 * - Ktor route suspend context에서는 [graphSuspendOperations]를 우선 사용한다.
 * - plugin이 설치되지 않았으면 [IllegalStateException]을 던진다.
 */
fun Application.graphOperations(): GraphOperations =
    graphPluginState().graphOperations

/**
 * 현재 [Application]에서 coroutine 우선 [GraphSuspendOperations]를 반환한다.
 *
 * ## 동작 계약
 * - Ktor route handler와 coroutine service에서 우선 사용할 facade다.
 * - plugin이 설치되지 않았으면 [IllegalStateException]을 던진다.
 */
fun Application.graphSuspendOperations(): GraphSuspendOperations =
    graphPluginState().graphSuspendOperations

/**
 * route handler의 [ApplicationCall]에서 blocking [GraphOperations]를 반환한다.
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
 * route handler의 [ApplicationCall]에서 coroutine 우선 [GraphSuspendOperations]를 반환한다.
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

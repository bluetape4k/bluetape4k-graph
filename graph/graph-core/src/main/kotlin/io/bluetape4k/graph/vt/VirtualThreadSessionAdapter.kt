package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.repository.GraphSession
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * [GraphSession] 의 lifecycle 조작을 Virtual Thread 위에서 실행하는 어댑터.
 *
 * 모든 작업에 `virtualFutureOf { }` 를 사용한다.
 *
 * @param delegate 위임할 동기 [GraphSession].
 */
class VirtualThreadSessionAdapter(
    private val delegate: GraphSession,
) : GraphVirtualThreadSession {

    companion object : KLogging()

    override fun createGraphAsync(name: String): CompletableFuture<Unit> =
        virtualFutureOf { delegate.createGraph(name) }

    override fun dropGraphAsync(name: String): CompletableFuture<Unit> =
        virtualFutureOf { delegate.dropGraph(name) }

    override fun graphExistsAsync(name: String): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.graphExists(name) }
}

/**
 * [GraphSession] 을 Virtual Thread 세션 어댑터로 감싸는 확장 함수.
 */
fun GraphSession.asVirtualThreadSession(): GraphVirtualThreadSession =
    VirtualThreadSessionAdapter(this)

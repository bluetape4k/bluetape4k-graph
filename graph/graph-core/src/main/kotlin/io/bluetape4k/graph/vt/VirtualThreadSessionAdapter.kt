package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.VirtualThreadExecutor
import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.repository.GraphSession
import io.bluetape4k.graph.repository.GraphVirtualThreadSession
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * [GraphSession] 의 lifecycle 조작을 Virtual Thread 위에서 실행하는 어댑터.
 *
 * 단일 작업에는 `virtualFutureOf { }` / `VirtualThreadExecutor` 를 사용한다.
 * `StructuredTaskScopes` 는 여러 작업을 병렬 실행할 때 쓰인다.
 *
 * @param delegate 위임할 동기 [GraphSession].
 */
class VirtualThreadSessionAdapter(
    private val delegate: GraphSession,
) : GraphVirtualThreadSession {

    companion object : KLogging()

    override fun createGraphAsync(name: String): CompletableFuture<Void> =
        CompletableFuture.runAsync({ delegate.createGraph(name) }, VirtualThreadExecutor)

    override fun dropGraphAsync(name: String): CompletableFuture<Void> =
        CompletableFuture.runAsync({ delegate.dropGraph(name) }, VirtualThreadExecutor)

    override fun graphExistsAsync(name: String): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.graphExists(name) }
}

/**
 * [GraphSession] 을 Virtual Thread 세션 어댑터로 감싸는 확장 함수.
 */
fun GraphSession.asVirtualThreadSession(): GraphVirtualThreadSession =
    VirtualThreadSessionAdapter(this)

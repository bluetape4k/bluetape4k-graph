package io.bluetape4k.graph.repository

import java.util.concurrent.CompletableFuture

/**
 * Virtual Threads로 graph session을 관리한다.
 *
 * 각 method는 blocking [GraphSession] operation을 Virtual Thread에서 실행한 결과를 담은
 * `CompletableFuture<T>`를 반환한다.
 */
interface GraphVirtualThreadSession {
    fun createGraphAsync(name: String): CompletableFuture<Unit>
    fun dropGraphAsync(name: String): CompletableFuture<Unit>
    fun graphExistsAsync(name: String): CompletableFuture<Boolean>
}

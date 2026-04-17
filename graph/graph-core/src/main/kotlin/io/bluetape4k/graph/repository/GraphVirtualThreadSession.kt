package io.bluetape4k.graph.repository

import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread 기반 그래프 세션 관리.
 *
 * 모든 메서드는 `CompletableFuture<T>` 를 반환하며,
 * 동기 [GraphSession] 을 Virtual Thread 위에서 실행한 결과를 담는다.
 * Java interop 목적이므로 `Unit` 대신 `Void` 를 사용한다.
 */
interface GraphVirtualThreadSession {
    fun createGraphAsync(name: String): CompletableFuture<Void>
    fun dropGraphAsync(name: String): CompletableFuture<Void>
    fun graphExistsAsync(name: String): CompletableFuture<Boolean>
}

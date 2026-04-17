package io.bluetape4k.concurrent.virtualthread

import java.util.concurrent.CompletableFuture

/**
 * nullable 결과를 반환하는 block을 Virtual Thread 위에서 비동기로 실행하고 [CompletableFuture]를 반환합니다.
 *
 * [virtualFutureOf]와 달리 `V` 타입에 `Any` 제약이 없어 nullable 반환 타입에 사용할 수 있습니다.
 *
 * ```kotlin
 * val future: CompletableFuture<GraphVertex?> = virtualFutureOfNullable {
 *     repository.findVertexById(label, id)  // GraphVertex? 반환
 * }
 * val result: GraphVertex? = future.join()
 * ```
 *
 * @param V 작업 결과 타입 (nullable 허용)
 * @param block 비동기로 수행할 작업
 * @return Virtual Thread 위에서 [block]을 실행하는 [CompletableFuture] 인스턴스
 */
inline fun <V> virtualFutureOfNullable(
    crossinline block: () -> V?,
): CompletableFuture<V?> =
    CompletableFuture.supplyAsync({ block() }, VirtualThreadExecutor)

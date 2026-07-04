package io.bluetape4k.concurrent.virtualthread

import java.util.concurrent.CompletableFuture

/**
 * Runs a nullable-result block asynchronously on a virtual thread and returns a [CompletableFuture].
 *
 * Unlike [virtualFutureOf], `V` is not bounded by `Any`, so nullable result types are supported.
 *
 * ```kotlin
 * val future: CompletableFuture<GraphVertex?> = virtualFutureOfNullable {
 *     repository.findVertexById(label, id)  // returns GraphVertex?
 * }
 * val result: GraphVertex? = future.join()
 * ```
 *
 * @param V task result type, including nullable values.
 * @param block task to run asynchronously.
 * @return [CompletableFuture] that runs [block] on a virtual thread.
 */
inline fun <V> virtualFutureOfNullable(
    crossinline block: () -> V?,
): CompletableFuture<V?> =
    CompletableFuture.supplyAsync({ block() }, VirtualThreadExecutor)

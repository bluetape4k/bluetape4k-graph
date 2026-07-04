package io.bluetape4k.graph.repository

import java.util.concurrent.CompletableFuture

/**
 * Manages graph sessions with Virtual Threads.
 *
 * Each method returns a `CompletableFuture<T>` containing the result of running
 * the blocking [GraphSession] operation on a Virtual Thread.
 */
interface GraphVirtualThreadSession {
    fun createGraphAsync(name: String): CompletableFuture<Unit>
    fun dropGraphAsync(name: String): CompletableFuture<Unit>
    fun graphExistsAsync(name: String): CompletableFuture<Boolean>
}

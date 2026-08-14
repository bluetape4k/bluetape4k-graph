@file:Suppress("TooGenericExceptionCaught")

package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.contract.GraphVirtualThreadBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Sync importer/exporter를 Virtual Thread 기반 CompletableFuture로 감싸는 어댑터.
 * public future와 worker thread를 분리해 `cancel(false)`/`cancel(true)` 의미를
 * 명시적으로 보존한다. `cancel(false)`는 future 상태만 취소하고, `cancel(true)`는
 * worker interrupt를 요청한다.
 */
object VirtualThreadGraphBulkAdapter : KLogging() {

    /**
     * 동기 임포터를 Virtual Thread 비동기 임포터로 감싼다.
     *
     * 반환 어댑터의 [AutoCloseable.close]는 delegate의 close를 최대 한 번만
     * 호출하며, 반복 호출은 no-op이다. source 소유권과 비동기 작업 중 close
     * 시점의 의미는 delegate 계약을 따른다.
     */
    fun <S : Any> wrapImporter(sync: GraphBulkImporter<S>): GraphVirtualThreadBulkImporter<S> =
        object : GraphVirtualThreadBulkImporter<S> {
            private val delegateClose = CloseOnce(sync)

            override fun close() = delegateClose.close()

            override fun importGraphAsync(
                source: S,
                operations: GraphOperations,
                options: GraphImportOptions,
            ) = cancellableVirtualFuture { sync.importGraph(source, operations, options) }
        }

    /**
     * 동기 익스포터를 Virtual Thread 비동기 익스포터로 감싼다.
     *
     * 반환 어댑터의 [AutoCloseable.close]는 delegate의 close를 최대 한 번만
     * 호출하며, 반복 호출은 no-op이다. sink 소유권과 비동기 작업 중 close
     * 시점의 의미는 delegate 계약을 따른다.
     */
    fun <T : Any> wrapExporter(sync: GraphBulkExporter<T>): GraphVirtualThreadBulkExporter<T> =
        object : GraphVirtualThreadBulkExporter<T> {
            private val delegateClose = CloseOnce(sync)

            override fun close() = delegateClose.close()

            override fun exportGraphAsync(
                sink: T,
                operations: GraphOperations,
                options: GraphExportOptions,
            ) = cancellableVirtualFuture { sync.exportGraph(sink, operations, options) }
        }

    /** Virtual Thread worker와 public future를 연결하는 취소 가능한 제출기. */
    fun <T> cancellableVirtualFuture(
        onCancel: (mayInterruptIfRunning: Boolean, started: Boolean) -> Unit = { _, _ -> },
        block: () -> T,
    ): CompletableFuture<T> {
        val future = CancellableVirtualFuture(block, onCancel)
        future.start()
        return future
    }

    /** Reporter lifecycle을 취소 요청 이후 작업 종료 시점까지 유지하는 VT helper. */
    fun <T> cancellableVirtualFuture(
        reporter: GraphIoProgressReporter,
        block: () -> T,
        onCompleted: (T) -> Unit,
    ): CompletableFuture<T> {
        val cancellationRequested = AtomicBoolean(false)
        return cancellableVirtualFuture(
            onCancel = { _, started ->
                cancellationRequested.set(true)
                if (!started) reporter.cancelled()
            },
        ) {
            reporter.run(
                block = block,
                onCompleted = { result ->
                    if (cancellationRequested.get()) reporter.cancelled() else onCompleted(result)
                },
            )
        }
    }

    private class CloseOnce(private val delegate: AutoCloseable) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) delegate.close()
        }
    }

    private class CancellableVirtualFuture<T>(
        private val block: () -> T,
        private val onCancel: (Boolean, Boolean) -> Unit,
    ) : CompletableFuture<T>() {

        private val worker = AtomicReference<Thread>()
        private val cancelRequested = AtomicBoolean(false)
        private val started = AtomicBoolean(false)
        private val startGate = CountDownLatch(1)
        private val lifecycleLock = ReentrantLock()

        fun start() {
            val thread = Thread.ofVirtual().unstarted {
                try {
                    startGate.await()
                    lifecycleLock.withLock {
                        if (cancelRequested.get()) return@unstarted
                        started.set(true)
                    }
                    complete(block())
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    completeExceptionally(error)
                } catch (error: Throwable) {
                    completeExceptionally(error)
                }
            }
            worker.set(thread)
            thread.start()
            startGate.countDown()
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            val cancellation = lifecycleLock.withLock {
                if (cancelRequested.get()) return@withLock false to false
                val didCancel = super.cancel(false)
                if (didCancel) {
                    cancelRequested.set(true)
                    didCancel to started.get()
                } else {
                    false to false
                }
            }
            val accepted = cancellation.first
            val running = cancellation.second
            if (!accepted) return false

            var callbackError: Error? = null
            try {
                onCancel(mayInterruptIfRunning, running)
            } catch (error: Error) {
                // Cancellation state must not be undone by an observer hook, but
                // an Error from a lifecycle listener remains visible to the caller.
                callbackError = error
            } catch (_: Exception) {
                // Listener/observer exceptions are isolated after cancellation is accepted.
            }
            if (mayInterruptIfRunning) {
                worker.get()?.interrupt()
            }
            callbackError?.let { throw it }
            return accepted
        }
    }
}

@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "ReturnCount",
    "ThrowsCount",
    "TooGenericExceptionCaught",
    "UseCheckOrError",
)

package io.bluetape4k.graph.io.nativebulk

import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class GraphNativeBulkLoadCancellationToken internal constructor(
    val startedNanos: Long,
    val timeoutNanos: Long,
    private val cancellationHook: (
        GraphNativeBulkLoadCancellationReason,
        GraphNativeBulkLoadDeadline,
    ) -> GraphNativeBulkLoadException?,
) {
    private val deadlineNanos: Long = saturatingAdd(startedNanos, timeoutNanos)
    private val requestedReason = AtomicReference<GraphNativeBulkLoadCancellationReason?>(null)
    private val hookInvoked = AtomicBoolean(false)
    private val hookFailure = AtomicReference<GraphNativeBulkLoadException?>(null)

    val reason: GraphNativeBulkLoadCancellationReason?
        get() = requestedReason.get()

    val isCancellationRequested: Boolean
        get() = requestedReason.get() != null

    fun remainingNanos(): Long {
        return saturatingSubtract(deadlineNanos, System.nanoTime()).coerceAtLeast(0L)
    }

    fun deadline(): GraphNativeBulkLoadDeadline = GraphNativeBulkLoadDeadline(deadlineNanos)

    /** Atomically records the first reason and invokes the bounded hook exactly once. */
    fun request(
        reason: GraphNativeBulkLoadCancellationReason,
        deadline: GraphNativeBulkLoadDeadline = closeGraceDeadline(),
    ): Boolean {
        if (!requestedReason.compareAndSet(null, reason)) return false
        if (hookInvoked.compareAndSet(false, true)) {
            cancellationHook(reason, deadline)?.let { hookFailure.compareAndSet(null, it) }
        }
        return true
    }

    internal fun cancellationHookFailure(): GraphNativeBulkLoadException? = hookFailure.get()

    fun check() {
        val loadDeadline = deadline()
        if (Thread.currentThread().isInterrupted) {
            request(GraphNativeBulkLoadCancellationReason.INTERRUPT, loadDeadline)
        }
        if (remainingNanos() == 0L) {
            request(GraphNativeBulkLoadCancellationReason.TIMEOUT, loadDeadline)
        }
        requestedReason.get()?.let { reason ->
            throw GraphNativeBulkLoadCancellationException(reason)
        }
    }
}


abstract class GraphNativeBulkLoadValidatedSource<V : Any> : AutoCloseable {
    private enum class State { OPEN, CLOSING, CLOSED }

    private val lifecycleLock = ReentrantLock()
    private val lifecycleChanged: Condition = lifecycleLock.newCondition()
    private var state = State.OPEN
    private var taken = false
    private var takeInFlight = false
    private var takingThread: Thread? = null
    private val closeStarted = AtomicBoolean(false)

    /** Validation 결과에 결합된 canonical/pinned artifact를 단 한 번만 소비한다. */
    final fun take(): V {
        lifecycleLock.lock()
        try {
            check(state == State.OPEN && !taken) { "validated source is not available" }
            taken = true
            takeInFlight = true
            takingThread = Thread.currentThread()
        } finally {
            lifecycleLock.unlock()
        }
        val takeDeadline = closeGraceDeadline()
        var value: V? = null
        var primaryFailure: Throwable? = null
        var interrupted = Thread.interrupted()
        var deferredCleanupOwner = false
        try {
            value = takeOnce(takeDeadline)
        } catch (failure: GraphNativeBulkLoadCancellationException) {
            primaryFailure = failure
        } catch (failure: GraphNativeBulkLoadException) {
            primaryFailure = redactNativeBulkLoadFailure(failure)
        } catch (_: InterruptedException) {
            interrupted = true
            primaryFailure = GraphNativeBulkLoadCancellationException(
                lifecycleLock.runLocked {
                    if (state == State.CLOSING) {
                        GraphNativeBulkLoadCancellationReason.CLOSE
                    } else {
                        GraphNativeBulkLoadCancellationReason.INTERRUPT
                    }
                },
            )
        } catch (_: Throwable) {
            primaryFailure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
        } finally {
            lifecycleLock.lock()
            try {
                takeInFlight = false
                takingThread = null
                if (state == State.CLOSING && closeStarted.compareAndSet(false, true)) {
                    deferredCleanupOwner = true
                }
                lifecycleChanged.signalAll()
            } finally {
                lifecycleLock.unlock()
            }
        }
        if (deferredCleanupOwner) {
            val deferredDeadline = closeGraceDeadline()
            val deferredCall = runBounded(deferredDeadline) { closeOnce(deferredDeadline) }
            if (deferredCall.completed) publishClosed()
            else deferredCall.onCompletion { publishClosed() }
            deferredCall.failure?.let { boundedFailure ->
                val existingFailure = primaryFailure
                if (existingFailure == null) primaryFailure = boundedFailure
                else existingFailure.addSuppressed(boundedFailure)
            }
        }
        if (Thread.interrupted()) interrupted = true
        if (interrupted) Thread.currentThread().interrupt()
        primaryFailure?.let { throw it }
        checkNotNull(value) { "validated source did not produce an artifact" }
        return value
    }

    /** Legacy adapter hook retained for source compatibility. */
    protected open fun takeOnce(): V =
        error("validated source must implement takeOnce() or takeOnce(deadline)")

    /** Deadline-aware hook for adapters that can interrupt or cancel source acquisition. */
    protected open fun takeOnce(deadline: GraphNativeBulkLoadDeadline): V = takeOnce()

    final override fun close() {
        val deadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(System.nanoTime(), GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        )
        var interrupted = Thread.interrupted()
        var ownsClose = false
        var timedOut = false
        var threadToInterrupt: Thread? = null
        val currentThread = Thread.currentThread()
        lifecycleLock.lock()
        try {
            if (takingThread === currentThread) {
                if (interrupted) currentThread.interrupt()
                throw IllegalStateException("close() cannot be re-entered from an active take()")
            }
            when (state) {
                State.OPEN -> {
                    state = State.CLOSING
                    threadToInterrupt = takingThread
                }
                State.CLOSING -> threadToInterrupt = takingThread
                State.CLOSED -> Unit
            }
            threadToInterrupt?.takeUnless { it === currentThread }?.interrupt()
            threadToInterrupt = null
            while (takeInFlight) {
                val remaining = deadline.remainingNanos()
                if (remaining == 0L) {
                    timedOut = true
                    break
                }
                try {
                    lifecycleChanged.awaitNanos(remaining)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            if (!timedOut && state == State.CLOSING && closeStarted.compareAndSet(false, true)) {
                ownsClose = true
            } else if (!timedOut) {
                while (state != State.CLOSED && closeStarted.get()) {
                    val remaining = deadline.remainingNanos()
                    if (remaining == 0L) {
                        timedOut = true
                        break
                    }
                    try {
                        lifecycleChanged.awaitNanos(remaining)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                ownsClose = !timedOut && state != State.CLOSED
                if (ownsClose) closeStarted.set(true)
            }
        } finally {
            lifecycleLock.unlock()
        }
        if (!ownsClose) {
            if (interrupted) Thread.currentThread().interrupt()
            if (timedOut) throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.TIMEOUT)
            return
        }
        if (interrupted) Thread.interrupted()
        val cleanupCall = runBounded(deadline) { closeOnce(deadline) }
        if (cleanupCall.completed) publishClosed()
        else cleanupCall.onCompletion { publishClosed() }
        lifecycleLock.lock()
        try {
            if (Thread.interrupted()) interrupted = true
        } finally {
            lifecycleLock.unlock()
        }
        if (interrupted) Thread.currentThread().interrupt()
        cleanupCall.failure?.let { throw it }
    }

    /** Must attempt every independent resource, aggregate failures, and be terminal and deadline-aware. */
    protected abstract fun closeOnce(deadline: GraphNativeBulkLoadDeadline)

    private fun publishClosed() {
        lifecycleLock.lock()
        try {
            state = State.CLOSED
            lifecycleChanged.signalAll()
        } finally {
            lifecycleLock.unlock()
        }
    }
}

class GraphNativeBulkLoadExecution<V : Any>(
    val operationName: String,
    val timeout: Duration,
    val maxFailureDetails: Int,
    val progressInterval: Long,
    val source: GraphNativeBulkLoadValidatedSource<V>,
    val cancellation: GraphNativeBulkLoadCancellationToken,
) {
    val timeoutNanos: Long
        get() = cancellation.timeoutNanos

    fun remainingNanos(): Long = cancellation.remainingNanos()

    override fun toString(): String =
        "GraphNativeBulkLoadExecution(timeout=$timeout, " +
            "maxFailureDetails=$maxFailureDetails, progressInterval=$progressInterval)"
}

fun interface GraphNativeBulkLoadSourceValidator<R : Any, V : Any> {
    /** Native command 전에 source kind, trust policy와 실행 위치를 검증한다. */
    fun validate(
        request: GraphNativeBulkLoadRequest<R>,
        capabilities: GraphNativeBulkLoaderCapabilities,
        cancellation: GraphNativeBulkLoadCancellationToken,
        validation: GraphNativeBulkLoadValidationContext,
    ): GraphNativeBulkLoadValidatedSource<V>
}

/** Validator가 반환 전에 만든 provisional resource의 rollback 소유권을 명시한다. */
class GraphNativeBulkLoadValidationContext {
    private val rollbackLock = ReentrantLock()
    private val rollbackActions = ArrayDeque<AutoCloseable>()
    private val pendingRollbackCalls = mutableSetOf<GraphNativeBulkLoadBoundedCall>()
    private var committed = false

    fun registerRollback(action: AutoCloseable) {
        rollbackLock.lock()
        try {
            check(!committed) { "validation rollback context is already committed" }
            rollbackActions.addFirst(action)
        } finally {
            rollbackLock.unlock()
        }
    }

    internal fun commit() {
        rollbackLock.lock()
        try {
            check(!committed) { "validation rollback context is already committed" }
            committed = true
            rollbackActions.clear()
        } finally {
            rollbackLock.unlock()
        }
    }

    internal fun rollback(deadline: GraphNativeBulkLoadDeadline): GraphNativeBulkLoadException? {
        val actions = rollbackLock.runLocked {
            if (committed) return null
            committed = true
            val snapshot = rollbackActions.toList()
            rollbackActions.clear()
            snapshot
        }
        if (actions.isEmpty()) return null
        val lateFailure = AtomicReference<GraphNativeBulkLoadException?>(null)
        val rollbackDeadline = boundedByCloseGrace(deadline)
        val rollbackCall = runBounded(rollbackDeadline) {
            actions.forEach { action ->
                try {
                    action.close()
                } catch (caught: GraphNativeBulkLoadException) {
                    lateFailure.updateAndGet { current ->
                        mergeRedactedFailure(current, redactNativeBulkLoadFailure(caught))
                    }
                } catch (_: Exception) {
                    lateFailure.updateAndGet { current ->
                        mergeRedactedFailure(
                            current,
                            GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN),
                        )
                    }
                }
            }
        }
        if (!rollbackCall.completed) {
            rollbackLock.runLocked { pendingRollbackCalls.add(rollbackCall) }
            rollbackCall.onCompletion {
                rollbackLock.runLocked { pendingRollbackCalls.remove(rollbackCall) }
            }
        }
        var failure = lateFailure.get()
        rollbackCall.failure?.let { failure = mergeRedactedFailure(failure, it) }
        return failure
    }
}

private inline fun <T> ReentrantLock.runLocked(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

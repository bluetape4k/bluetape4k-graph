@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "ThrowsCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
    "UseCheckOrError",
    "UseRequireNotNull",
)

package io.bluetape4k.graph.io.nativebulk

import java.io.Serializable
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/** Caller thread에서 누적 progress를 받는 listener다. */
fun interface GraphNativeBulkLoadProgressListener {
    fun onProgress(progress: GraphNativeBulkLoadProgress)
}

private class GraphNativeBulkLoadListenerFailure(
    val original: Throwable,
    val cancellationFailure: GraphNativeBulkLoadException?,
) : RuntimeException()

/** Loader lifecycle diagnostic의 종류다. */
enum class GraphNativeBulkLoadDiagnosticKind {
    STARTED, COMPLETED, FAILED, CANCELLED, CLOSED
}

/** Secret-free lifecycle correlation에 사용하는 diagnostic snapshot이다. */
data class GraphNativeBulkLoadDiagnostic(
    val diagnosticId: String,
    val kind: GraphNativeBulkLoadDiagnosticKind,
    val backend: String,
    val operationName: String,
    val phase: GraphNativeBulkLoadPhase?,
    val elapsed: Duration,
    val outcome: GraphNativeBulkLoadOutcome?,
    val code: GraphNativeBulkLoadFailureCode?,
    val cancellationReason: GraphNativeBulkLoadCancellationReason? = null,
) : Serializable {
    init {
        diagnosticId.requireLogSafe("diagnosticId", 64)
        backend.requireLogSafe("backend", GraphNativeBulkLoaderCapabilities.MAX_BACKEND_LENGTH)
        operationName.requireLogSafe(
            "operationName",
            GraphNativeBulkLoadRequest.MAX_OPERATION_NAME_LENGTH,
        )
        require(operationName == GraphNativeBulkLoadRequest.REQUIRED_OPERATION_NAME) {
            "operationName must be the fixed native-bulk-load operation label"
        }
        require(elapsed >= Duration.ZERO) { "elapsed must be >= 0" }
        when (kind) {
            GraphNativeBulkLoadDiagnosticKind.COMPLETED -> {
                require(outcome == GraphNativeBulkLoadOutcome.COMPLETED) {
                    "COMPLETED diagnostic requires a COMPLETED outcome"
                }
            }
            GraphNativeBulkLoadDiagnosticKind.CANCELLED -> {
                require(outcome == GraphNativeBulkLoadOutcome.CANCELLED) {
                    "CANCELLED diagnostic requires a CANCELLED outcome"
                }
                require(cancellationReason != null) {
                    "CANCELLED diagnostic requires a cancellation reason"
                }
            }
            GraphNativeBulkLoadDiagnosticKind.FAILED -> {
                require(outcome != GraphNativeBulkLoadOutcome.COMPLETED) {
                    "FAILED diagnostic cannot report a COMPLETED outcome"
                }
            }
            GraphNativeBulkLoadDiagnosticKind.STARTED,
            GraphNativeBulkLoadDiagnosticKind.CLOSED -> Unit
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** Bounded lifecycle diagnostic을 선택적으로 받는 observer다. */
fun interface GraphNativeBulkLoadDiagnosticObserver {
    /** Observer failure는 redaction 후 삼키며 load outcome을 바꾸지 않는다. */
    fun onDiagnostic(diagnostic: GraphNativeBulkLoadDiagnostic)
}

private class GraphNativeBulkLoadProgressVerifier(
    private val progressInterval: Long,
    private val callerThread: Thread,
    private val listener: GraphNativeBulkLoadProgressListener?,
    private val cancel: () -> GraphNativeBulkLoadException?,
) {
    private var previous: GraphNativeBulkLoadProgress? = null
    private var previousPhase = -1
    private var callbackCount = 0L
    private var completeSeen = false

    fun onProgress(progress: GraphNativeBulkLoadProgress) {
        contractRequire(Thread.currentThread() === callerThread) {
            "progress callbacks must run on the load caller thread"
        }
        contractRequire(!completeSeen) { "progress cannot be emitted after COMPLETE" }
        contractRequire(progress.phase.ordinal >= previousPhase) { "progress phase regressed" }
        previous?.let {
            contractRequire(progress.processed >= it.processed) { "processed count regressed" }
            contractRequire(progress.succeeded >= it.succeeded) { "succeeded count regressed" }
            contractRequire(progress.failed >= it.failed) { "failed count regressed" }
        }
        if (progress.eventKind == GraphNativeBulkLoadProgressEventKind.PHASE) {
            contractRequire(
                if (previous == null) progress.phase == GraphNativeBulkLoadPhase.PREPARE
                else progress.phase.ordinal > previousPhase,
            ) {
                "phase progress must represent a phase transition"
            }
        } else {
            contractRequire(previous != null) { "interval progress requires an initial PHASE event" }
            contractRequire(progress.phase.ordinal == previousPhase) {
                "interval progress must remain within the current phase"
            }
            contractRequire(progress.phase != GraphNativeBulkLoadPhase.COMPLETE) {
                "interval progress cannot be emitted at COMPLETE"
            }
            val previousProgress = requireNotNull(previous)
            contractRequire(progress.processed > previousProgress.processed) {
                "interval progress must advance processed count"
            }
            val previousBucket = previousProgress.processed / progressInterval
            val currentBucket = progress.processed / progressInterval
            contractRequire(currentBucket > previousBucket) {
                "interval progress must cross a progress token boundary"
            }
        }
        val buckets = if (progress.processed == 0L) 0L
        else 1L + (progress.processed - 1L) / progressInterval
        val intervalBudget = if (buckets > Long.MAX_VALUE - 5L) {
            Long.MAX_VALUE
        } else {
            5L + buckets
        }
        val maxCallbacks = minOf(GraphNativeBulkLoadRequest.MAX_PROGRESS_CALLBACKS, intervalBudget)
        contractRequire(callbackCount < maxCallbacks) { "progress callback budget exceeded" }
        callbackCount++
        previousPhase = progress.phase.ordinal
        previous = progress
        if (progress.phase == GraphNativeBulkLoadPhase.COMPLETE) {
            completeSeen = true
        }
        try {
            listener?.onProgress(progress)
        } catch (failure: Throwable) {
            throw GraphNativeBulkLoadListenerFailure(failure, cancel())
        }
    }

    fun requireTerminal(report: GraphNativeBulkLoadReport) {
        contractRequire(completeSeen) { "load must emit exactly one COMPLETE progress event" }
        val terminal = requireNotNull(previous) { "missing terminal progress" }
        contractRequire(terminal.outcome == report.outcome) {
            "terminal progress outcome does not match report"
        }
        contractRequire(terminal.processed == report.processed &&
            terminal.succeeded == report.succeeded &&
            terminal.failed == report.failed
        ) {
            "terminal progress counts do not match report"
        }
    }

    fun lastPhase(): GraphNativeBulkLoadPhase? = previous?.phase
}

private val graphNativeBulkLoadDiagnosticSequence = AtomicLong()

/** Raw request 검증과 typed native command 실행을 조율하는 base SPI다. */
abstract class GraphNativeBulkLoader<R : Any, V : Any>(
    final val capabilities: GraphNativeBulkLoaderCapabilities,
    final val sourceValidator: GraphNativeBulkLoadSourceValidator<R, V>,
    private val diagnosticObserver: GraphNativeBulkLoadDiagnosticObserver? = null,
) : AutoCloseable {
    private enum class State { OPEN, LOADING, CLOSING, CLOSED }

    private val lifecycleLock = ReentrantLock()
    private val lifecycleChanged: Condition = lifecycleLock.newCondition()
    private val diagnosticInFlight = AtomicBoolean(false)
    private val diagnosticDisabled = AtomicBoolean(false)
    private val pendingDiagnostic = AtomicReference<GraphNativeBulkLoadDiagnostic?>(null)
    /** Allows one bounded retry after the observer circuit opens. */
    private val timeoutDiagnosticAttempted = AtomicBoolean(false)
    private var state = State.OPEN
    private var loadInFlight = false
    private var terminalizationInFlight = false
    private var loadingThread: Thread? = null
    private val closeStarted = AtomicBoolean(false)
    private var closingThread: Thread? = null
    private var activeCancellation: GraphNativeBulkLoadCancellationToken? = null
    private var activeDiagnosticId: String? = null

    /** Request를 검증하고 progress/report/lifecycle 계약을 적용해 실행한다. */
    final fun load(
        request: GraphNativeBulkLoadRequest<R>,
        listener: GraphNativeBulkLoadProgressListener? = null,
    ): GraphNativeBulkLoadReport {
        val startedNanos = System.nanoTime()
        val diagnosticId = newDiagnosticId()
        requireSupportedRequest(request)
        val cancellation = beginLoad(request.timeout, diagnosticId)
        emitDiagnostic(
            kind = GraphNativeBulkLoadDiagnosticKind.STARTED,
            startedNanos = startedNanos,
            phase = GraphNativeBulkLoadPhase.PREPARE,
            operationName = request.operationName,
            deadline = cancellation.deadline(),
            diagnosticId = diagnosticId,
        )
        val verifier = GraphNativeBulkLoadProgressVerifier(
            progressInterval = request.progressInterval,
            callerThread = Thread.currentThread(),
            listener = listener,
            cancel = {
                cancellation.request(
                    GraphNativeBulkLoadCancellationReason.LISTENER_FAILURE,
                    boundedByCloseGrace(cancellation.deadline()),
                )
                cancellation.cancellationHookFailure()
            },
        )
        var validated: GraphNativeBulkLoadValidatedSource<V>? = null
        var report: GraphNativeBulkLoadReport? = null
        var primaryFailure: Throwable? = null
        var deferredCleanupOwner = false
        try {
            val validation = GraphNativeBulkLoadValidationContext()
            validated = try {
                val handle = sourceValidator.validate(request, capabilities, cancellation, validation)
                validation.commit()
                handle
            } catch (failure: GraphNativeBulkLoadException) {
                val redacted = redactNativeBulkLoadFailure(failure)
                validation.rollback(cancellation.deadline())?.let { redacted.addSuppressed(it) }
                throw redacted
            } catch (_: Exception) {
                val rejected = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.SOURCE_REJECTED)
                validation.rollback(cancellation.deadline())?.let { rejected.addSuppressed(it) }
                throw rejected
            } catch (_: Throwable) {
                val rejected = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.SOURCE_REJECTED)
                validation.rollback(cancellation.deadline())?.let { rejected.addSuppressed(it) }
                throw rejected
            }
            cancellation.check()
            report = try {
                loadValidated(
                    GraphNativeBulkLoadExecution(
                        operationName = request.operationName,
                        timeout = request.timeout ?: GraphNativeBulkLoadRequest.DEFAULT_TIMEOUT,
                        maxFailureDetails = request.maxFailureDetails,
                        progressInterval = request.progressInterval,
                        source = validated,
                        cancellation = cancellation,
                    ),
                    verifier::onProgress,
                )
            } catch (failure: GraphNativeBulkLoadListenerFailure) {
                throw failure
            } catch (failure: GraphNativeBulkLoadException) {
                throw redactNativeBulkLoadFailure(failure)
            } catch (_: IllegalArgumentException) {
                throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION)
            } catch (_: Throwable) {
                throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.NATIVE_COMMAND_FAILED)
            }
            cancellation.check()
            verifier.requireTerminal(report)
            report.requireCompatible(request, capabilities)
        } catch (failure: GraphNativeBulkLoadListenerFailure) {
            val listenerFailure = failure.original
            failure.cancellationFailure?.let { listenerFailure.addSuppressed(it) }
            primaryFailure = listenerFailure
        } catch (failure: GraphNativeBulkLoadException) {
            primaryFailure = redactNativeBulkLoadFailure(failure)
        } catch (_: IllegalArgumentException) {
            primaryFailure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.CONTRACT_VIOLATION)
        } catch (_: Throwable) {
            primaryFailure = GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
        } finally {
            validated?.let { source ->
                try {
                    source.close()
                } catch (failure: GraphNativeBulkLoadException) {
                    primaryFailure = mergeFailure(primaryFailure, redactNativeBulkLoadFailure(failure))
                } catch (_: Throwable) {
                    primaryFailure = mergeFailure(
                        primaryFailure,
                        GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN),
                    )
                }
            }
            beginTerminalization()
            val terminal = report
            fun emitLoadTerminal(
                finalFailure: Throwable?,
                diagnosticDeadline: GraphNativeBulkLoadDeadline,
            ) {
                val failureCode = (finalFailure as? GraphNativeBulkLoadException)?.code
                    ?: if (finalFailure != null) GraphNativeBulkLoadFailureCode.UNKNOWN else null
                val exceptionCancellation = (finalFailure as? GraphNativeBulkLoadCancellationException)?.reason
                val reportCancellation = terminal?.cancellationReason ?: exceptionCancellation ?: cancellation.reason
                val terminalCancellation = reportCancellation ?: when (failureCode) {
                    GraphNativeBulkLoadFailureCode.TIMEOUT -> GraphNativeBulkLoadCancellationReason.TIMEOUT
                    GraphNativeBulkLoadFailureCode.CANCELLED -> GraphNativeBulkLoadCancellationReason.CLOSE
                    else -> GraphNativeBulkLoadCancellationReason.LISTENER_FAILURE
                }
                val isCancelled = terminal?.outcome == GraphNativeBulkLoadOutcome.CANCELLED ||
                    reportCancellation != null ||
                    failureCode == GraphNativeBulkLoadFailureCode.CANCELLED ||
                    failureCode == GraphNativeBulkLoadFailureCode.TIMEOUT
                val diagnosticOutcome = when {
                    isCancelled -> GraphNativeBulkLoadOutcome.CANCELLED
                    finalFailure == null -> terminal?.outcome
                    terminal?.outcome == GraphNativeBulkLoadOutcome.PARTIAL ->
                        GraphNativeBulkLoadOutcome.PARTIAL
                    else -> GraphNativeBulkLoadOutcome.FAILED
                }
                emitDiagnostic(
                    kind = when {
                        finalFailure == null && terminal?.outcome == GraphNativeBulkLoadOutcome.COMPLETED ->
                            GraphNativeBulkLoadDiagnosticKind.COMPLETED
                        isCancelled ->
                            GraphNativeBulkLoadDiagnosticKind.CANCELLED
                        else -> GraphNativeBulkLoadDiagnosticKind.FAILED
                    },
                    startedNanos = startedNanos,
                    phase = verifier.lastPhase(),
                    outcome = diagnosticOutcome,
                    code = failureCode,
                    cancellationReason = if (isCancelled) terminalCancellation else null,
                    operationName = request.operationName,
                    deadline = diagnosticDeadline,
                    diagnosticId = diagnosticId,
                )
            }
            emitLoadTerminal(primaryFailure, cancellation.deadline())
            deferredCleanupOwner = finishTerminalization()
            if (deferredCleanupOwner) {
                val deferredDeadline = closeGraceDeadline()
                val deferredFailure = closeResourcesTerminal(
                    initialFailure = primaryFailure as? GraphNativeBulkLoadException,
                    startedNanos = startedNanos,
                    operationName = request.operationName,
                    diagnosticId = diagnosticId,
                    deadline = deferredDeadline,
                )
                deferredFailure?.let { primaryFailure = mergeFailure(primaryFailure, it) }
            }
        }
        primaryFailure?.let { throw it }
        return report ?: throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNKNOWN)
    }

    private fun beginLoad(timeout: Duration?, diagnosticId: String): GraphNativeBulkLoadCancellationToken {
        val effectiveTimeout = timeout ?: GraphNativeBulkLoadRequest.DEFAULT_TIMEOUT
        val cancellation = GraphNativeBulkLoadCancellationToken(
            startedNanos = System.nanoTime(),
            timeoutNanos = effectiveTimeout.toNanos(),
            cancellationHook = ::requestCancellationSafely,
        )
        lifecycleLock.lock()
        try {
            check(state == State.OPEN) { "native bulk loader is not open" }
            state = State.LOADING
            loadInFlight = true
            loadingThread = Thread.currentThread()
            activeCancellation = cancellation
            activeDiagnosticId = diagnosticId
        } finally {
            lifecycleLock.unlock()
        }
        return cancellation
    }

    private fun requireSupportedRequest(request: GraphNativeBulkLoadRequest<R>) {
        if (!capabilities.supported || request.sourceKind !in capabilities.sourceKinds) {
            throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE)
        }
    }

    /** Marks the command finished while keeping close behind terminal diagnostics. */
    private fun beginTerminalization() {
        lifecycleLock.lock()
        try {
            loadInFlight = false
            loadingThread = null
            activeCancellation = null
            terminalizationInFlight = true
            lifecycleChanged.signalAll()
        } finally {
            lifecycleLock.unlock()
        }
    }

    /** Releases the lifecycle barrier after terminal diagnostics have been emitted. */
    private fun finishTerminalization(): Boolean {
        lifecycleLock.lock()
        try {
            terminalizationInFlight = false
            val deferredOwner = state == State.CLOSING && closeStarted.compareAndSet(false, true)
            if (deferredOwner) closingThread = Thread.currentThread()
            if (state == State.LOADING) {
                state = State.OPEN
                activeDiagnosticId = null
            }
            lifecycleChanged.signalAll()
            return deferredOwner
        } finally {
            lifecycleLock.unlock()
        }
    }

    override fun close() {
        val closeStartedNanos = System.nanoTime()
        var diagnosticId = newDiagnosticId()
        var interrupted = Thread.interrupted()
        var closeFailure: GraphNativeBulkLoadException? = null
        var cancellation: GraphNativeBulkLoadCancellationToken? = null
        lifecycleLock.lock()
        try {
            val currentThread = Thread.currentThread()
            if ((state == State.LOADING && loadingThread === currentThread) ||
                (state == State.CLOSING && (loadingThread === currentThread || closingThread === currentThread))
            ) {
                if (interrupted) currentThread.interrupt()
                throw IllegalStateException("close() cannot be re-entered from an active lifecycle callback")
            }
            when (state) {
                State.OPEN -> {
                    state = State.CLOSING
                    activeDiagnosticId = diagnosticId
                }
                State.LOADING -> {
                    state = State.CLOSING
                    cancellation = activeCancellation
                    diagnosticId = activeDiagnosticId ?: diagnosticId
                }
                State.CLOSING -> {
                    cancellation = activeCancellation
                    diagnosticId = activeDiagnosticId ?: diagnosticId
                }
                State.CLOSED -> Unit
            }
        } finally {
            lifecycleLock.unlock()
        }
        if (stateSnapshot() == State.CLOSED) {
            if (interrupted) Thread.currentThread().interrupt()
            return
        }

        val closeDeadline = GraphNativeBulkLoadDeadline(
            saturatingAdd(closeStartedNanos, GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos()),
        )
        cancellation?.request(GraphNativeBulkLoadCancellationReason.CLOSE, closeDeadline)
        cancellation?.cancellationHookFailure()?.let { closeFailure = it }
        lifecycleLock.lock()
        try {
            loadingThread?.takeUnless { it === Thread.currentThread() }?.interrupt()
        } finally {
            lifecycleLock.unlock()
        }
        var ownsClose = false
        var closeTimedOut = false
        lifecycleLock.lock()
        try {
            while (loadInFlight || terminalizationInFlight) {
                val remaining = closeDeadline.remainingNanos()
                if (remaining <= 0L) {
                    closeTimedOut = true
                    break
                }
                try {
                    lifecycleChanged.awaitNanos(remaining)
                } catch (_: InterruptedException) {
                    interrupted = true
                    cancellation?.request(GraphNativeBulkLoadCancellationReason.INTERRUPT, closeDeadline)
                }
            }
            if (!closeTimedOut && state != State.CLOSED) {
                ownsClose = closeStarted.compareAndSet(false, true)
                if (ownsClose) closingThread = Thread.currentThread()
                while (!ownsClose && state != State.CLOSED) {
                    val remaining = closeDeadline.remainingNanos()
                    if (remaining <= 0L) {
                        closeTimedOut = true
                        break
                    }
                    try {
                        lifecycleChanged.awaitNanos(remaining)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
            }
        } finally {
            lifecycleLock.unlock()
        }
        cancellation?.cancellationHookFailure()?.let { closeFailure = mergeFailure(closeFailure, it) }
        if (closeTimedOut) closeFailure = mergeFailure(
            closeFailure,
            GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.TIMEOUT),
        )
        if (closeTimedOut) {
            emitDiagnostic(
                kind = GraphNativeBulkLoadDiagnosticKind.CANCELLED,
                startedNanos = closeStartedNanos,
                phase = GraphNativeBulkLoadPhase.COMPLETE,
                outcome = GraphNativeBulkLoadOutcome.CANCELLED,
                code = GraphNativeBulkLoadFailureCode.TIMEOUT,
                cancellationReason = cancellation?.reason ?: GraphNativeBulkLoadCancellationReason.CLOSE,
                deadline = closeDeadline,
                diagnosticId = diagnosticId,
            )
        }
        if (ownsClose) {
            closeFailure = closeResourcesTerminal(
                initialFailure = closeFailure,
                interruptedAtEntry = interrupted,
                startedNanos = closeStartedNanos,
                diagnosticId = diagnosticId,
                deadline = closeDeadline,
            )
        }
        if (interrupted) Thread.currentThread().interrupt()
        closeFailure?.let { throw it }
    }

    private fun closeResourcesTerminal(
        initialFailure: GraphNativeBulkLoadException?,
        interruptedAtEntry: Boolean = Thread.interrupted(),
        startedNanos: Long = System.nanoTime(),
        operationName: String = "native-bulk-load",
        diagnosticId: String = newDiagnosticId(),
        deadline: GraphNativeBulkLoadDeadline = closeGraceDeadline(),
        emitClosed: Boolean = true,
        onClosed: (GraphNativeBulkLoadException?) -> Unit = {},
    ): GraphNativeBulkLoadException? {
        var interrupted = interruptedAtEntry
        var failure = initialFailure
        if (interrupted) Thread.interrupted()
        val cleanupCall = runBounded(deadline) { closeResources(deadline) }

        fun publishClosed(finalFailure: GraphNativeBulkLoadException?) {
            lifecycleLock.lock()
            try {
                state = State.CLOSED
                closingThread = null
                activeDiagnosticId = null
                lifecycleChanged.signalAll()
            } finally {
                lifecycleLock.unlock()
            }
            if (emitClosed) {
                emitDiagnostic(
                    GraphNativeBulkLoadDiagnosticKind.CLOSED,
                    startedNanos,
                    GraphNativeBulkLoadPhase.COMPLETE,
                    code = finalFailure?.code,
                    operationName = operationName,
                    deadline = deadline,
                    diagnosticId = diagnosticId,
                )
            }
            onClosed(finalFailure)
        }

        if (cleanupCall.completed) {
            cleanupCall.failure?.let { failure = mergeFailure(failure, it) }
            publishClosed(failure)
        } else {
            cleanupCall.failure?.let { failure = mergeFailure(failure, it) }
            val initialForCallback = failure
            cleanupCall.onCompletion { lateFailure ->
                val finalFailure = lateFailure?.let { mergeFailure(initialForCallback, it) }
                    ?: initialForCallback
                publishClosed(finalFailure)
            }
        }
        if (Thread.interrupted()) interrupted = true
        if (interrupted) Thread.currentThread().interrupt()
        return failure
    }

    private fun stateSnapshot(): State {
        lifecycleLock.lock()
        return try { state } finally { lifecycleLock.unlock() }
    }

    private fun requestCancellationSafely(
        reason: GraphNativeBulkLoadCancellationReason,
        deadline: GraphNativeBulkLoadDeadline,
    ): GraphNativeBulkLoadException? {
        val interrupted = Thread.interrupted()
        var wasInterrupted = interrupted
        return try {
            val boundedDeadline = boundedByCloseGrace(deadline)
            runBounded(boundedDeadline) { requestCancellation(reason, boundedDeadline) }.failure
        } finally {
            if (Thread.interrupted()) wasInterrupted = true
            if (wasInterrupted) Thread.currentThread().interrupt()
        }
    }

    private fun emitDiagnostic(
        kind: GraphNativeBulkLoadDiagnosticKind,
        startedNanos: Long,
        phase: GraphNativeBulkLoadPhase?,
        outcome: GraphNativeBulkLoadOutcome? = null,
        code: GraphNativeBulkLoadFailureCode? = null,
        cancellationReason: GraphNativeBulkLoadCancellationReason? = null,
        operationName: String = "native-bulk-load",
        deadline: GraphNativeBulkLoadDeadline = closeGraceDeadline(),
        diagnosticId: String,
    ) {
        val observer = diagnosticObserver ?: return
        val elapsedNanos = (System.nanoTime() - startedNanos).coerceAtLeast(0L)
        val diagnostic = GraphNativeBulkLoadDiagnostic(
            diagnosticId = diagnosticId,
            kind = kind,
            backend = capabilities.backend,
            operationName = operationName,
            phase = phase,
            elapsed = Duration.ofNanos(elapsedNanos),
            outcome = outcome,
            code = code,
            cancellationReason = cancellationReason,
        )
        val timeoutDiagnostic = diagnostic.kind == GraphNativeBulkLoadDiagnosticKind.CANCELLED &&
            diagnostic.code == GraphNativeBulkLoadFailureCode.TIMEOUT
        if (diagnosticDisabled.get()) {
            if (timeoutDiagnostic) {
                pendingDiagnostic.set(diagnostic)
                dispatchPendingDiagnostic(observer)
            }
            return
        }
        if (!diagnosticInFlight.compareAndSet(false, true)) {
            if (timeoutDiagnostic) pendingDiagnostic.set(diagnostic)
            return
        }
        dispatchDiagnostic(observer, diagnostic, deadline, retryAttempt = false)
    }

    private fun dispatchDiagnostic(
        observer: GraphNativeBulkLoadDiagnosticObserver,
        diagnostic: GraphNativeBulkLoadDiagnostic,
        deadline: GraphNativeBulkLoadDeadline,
        retryAttempt: Boolean,
    ) {
        if (deadline.isExpired) {
            Thread.startVirtualThread {
                try {
                    val dispatchCall = runBounded(
                        GraphNativeBulkLoadDeadline(
                            saturatingAdd(
                                System.nanoTime(),
                                GraphNativeBulkLoadRequest.DEFAULT_CLOSE_GRACE.toNanos(),
                            ),
                        ),
                    ) {
                        observer.onDiagnostic(diagnostic)
                    }
                    if (!dispatchCall.completed) {
                        disableDiagnostics(retryAttempt)
                        dispatchCall.onCompletion {
                            diagnosticInFlight.set(false)
                            dispatchPendingDiagnostic(observer)
                        }
                    } else {
                        if (dispatchCall.failure?.code == GraphNativeBulkLoadFailureCode.TIMEOUT) {
                            disableDiagnostics(retryAttempt)
                        } else if (dispatchCall.failure == null) {
                            diagnosticDisabled.set(false)
                            timeoutDiagnosticAttempted.set(false)
                        }
                        diagnosticInFlight.set(false)
                        dispatchPendingDiagnostic(observer)
                    }
                } catch (_: Throwable) {
                    diagnosticInFlight.set(false)
                    dispatchPendingDiagnostic(observer)
                }
            }
            return
        }
        try {
            val observerDeadline = boundedByCloseGrace(deadline)
            val observerCall = runBounded(observerDeadline) {
                observer.onDiagnostic(diagnostic)
            }
            if (!observerCall.completed) {
                disableDiagnostics(retryAttempt)
                observerCall.onCompletion {
                    diagnosticInFlight.set(false)
                    dispatchPendingDiagnostic(observer)
                }
            } else {
                if (observerCall.failure?.code == GraphNativeBulkLoadFailureCode.TIMEOUT) {
                    disableDiagnostics(retryAttempt)
                } else if (observerCall.failure == null) {
                    diagnosticDisabled.set(false)
                    timeoutDiagnosticAttempted.set(false)
                }
                diagnosticInFlight.set(false)
                dispatchPendingDiagnostic(observer)
            }
        } catch (_: Throwable) {
            diagnosticInFlight.set(false)
            dispatchPendingDiagnostic(observer)
            // Observer setup failures are intentionally not part of the public outcome.
        }
    }

    private fun dispatchPendingDiagnostic(observer: GraphNativeBulkLoadDiagnosticObserver) {
        val pending = pendingDiagnostic.getAndSet(null) ?: return
        if (!diagnosticInFlight.compareAndSet(false, true)) {
            pendingDiagnostic.set(pending)
            return
        }
        val timeoutDiagnostic = pending.kind == GraphNativeBulkLoadDiagnosticKind.CANCELLED &&
            pending.code == GraphNativeBulkLoadFailureCode.TIMEOUT
        val retryAttempt = diagnosticDisabled.get() && timeoutDiagnostic &&
            timeoutDiagnosticAttempted.compareAndSet(false, true)
        if (diagnosticDisabled.get() && !retryAttempt) {
            diagnosticInFlight.set(false)
            pendingDiagnostic.set(pending)
            return
        }
        diagnosticDisabled.set(false)
        dispatchDiagnostic(
            observer,
            pending,
            // A pending event is already past its parent deadline; keep retry dispatch asynchronous
            // so a close/load caller never inherits a fresh observer grace period.
            GraphNativeBulkLoadDeadline(System.nanoTime()),
            retryAttempt = retryAttempt,
        )
    }

    private fun disableDiagnostics(retryAttempt: Boolean) {
        diagnosticDisabled.set(true)
        timeoutDiagnosticAttempted.set(retryAttempt)
    }

    private fun newDiagnosticId(): String =
        "diag-${graphNativeBulkLoadDiagnosticSequence.incrementAndGet().toString(36)}"

    /** Adapter native command에 bounded cancellation을 전달한다. */
    protected open fun requestCancellation(
        reason: GraphNativeBulkLoadCancellationReason,
        deadline: GraphNativeBulkLoadDeadline,
    ) {}

    /** 독립 resource를 모두 시도하고 failure를 합산하는 terminal cleanup hook이다. */
    protected open fun closeResources(deadline: GraphNativeBulkLoadDeadline) {}

    /** 검증된 typed source만 받아 native command를 실행한다. */
    protected abstract fun loadValidated(
        execution: GraphNativeBulkLoadExecution<V>,
        listener: GraphNativeBulkLoadProgressListener?,
    ): GraphNativeBulkLoadReport

    private fun mergeFailure(
        primary: GraphNativeBulkLoadException?,
        additional: GraphNativeBulkLoadException,
    ): GraphNativeBulkLoadException {
        if (primary == null) return additional
        primary.addSuppressed(additional)
        return primary
    }

    private fun mergeFailure(
        primary: Throwable?,
        additional: GraphNativeBulkLoadException,
    ): Throwable {
        if (primary == null) return additional
        primary.addSuppressed(additional)
        return primary
    }
}

/** Backend가 native bulk command를 지원하지 않음을 명시하는 loader다. */
class UnsupportedGraphNativeBulkLoader<R : Any, V : Any>(
    backend: String,
) : GraphNativeBulkLoader<R, V>(
    capabilities = GraphNativeBulkLoaderCapabilities(
        backend = backend,
        supported = false,
        sourceKinds = emptySet(),
        transactionGuarantee = GraphNativeBulkLoadTransactionGuarantee.UNKNOWN,
        failureDetail = GraphNativeBulkLoadFailureDetail.NONE,
    ),
    sourceValidator = GraphNativeBulkLoadSourceValidator { _, _, _, _ ->
        throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE)
    },
) {
    override fun loadValidated(
        execution: GraphNativeBulkLoadExecution<V>,
        listener: GraphNativeBulkLoadProgressListener?,
    ): GraphNativeBulkLoadReport =
        throw GraphNativeBulkLoadException(GraphNativeBulkLoadFailureCode.UNSUPPORTED_SOURCE)
}

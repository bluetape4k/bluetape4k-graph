@file:Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "SwallowedException",
)

package io.bluetape4k.graph.io.report

import io.bluetape4k.logging.KLogging
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 한 public 벌크 I/O 호출의 lifecycle과 누적 snapshot을 소유하는 내부 reporter.
 * callback은 lock을 보유한 작업 thread에서 직렬 호출되며, ReentrantLock이므로
 * listener의 재진입 호출도 교착 없이 별도 snapshot으로 처리된다.
 */
class GraphIoProgressReporter(
    private val operation: GraphIoOperation,
    private val format: GraphIoFormat,
    private val listener: GraphIoProgressListener,
    private val bytesProvider: () -> Long? = { null },
) : KLogging() {

    private enum class State {
        NEW,
        STARTED,
        TERMINAL,
    }

    private data class Snapshot(
        val vertices: Long = 0L,
        val successfulVertices: Long = 0L,
        val edges: Long = 0L,
        val successfulEdges: Long = 0L,
        val skippedVertices: Long = 0L,
        val skippedEdges: Long = 0L,
        val failures: Long = 0L,
        val bytesProcessed: Long? = null,
        val bytesTotal: Long? = null,
        val elapsed: Duration = Duration.ZERO,
    )

    private val lifecycleLock = ReentrantLock()
    private var state: State = State.NEW
    private var snapshot = Snapshot()

    val runId: Long = RUN_IDS.incrementAndGet()

    init {
        requireNotNull(listener) { "listener must not be null" }
    }

    /** 시작 event를 한 번만 발행한다. */
    fun started(): GraphIoProgressEvent? = lifecycleLock.withLock {
        if (state != State.NEW) return@withLock null
        state = State.STARTED
        try {
            dispatch(
                event = event(
                    type = GraphIoProgressEventType.STARTED,
                    hasStarted = true,
                    status = null,
                    phase = null,
                    phaseElapsed = null,
                    next = Snapshot(),
                )
            )
        } catch (error: Error) {
            terminalAfterListenerError(error)
            throw error
        }
    }

    /** 누적 progress snapshot을 발행한다. 시작 전 또는 terminal 이후에는 무시한다. */
    fun progress(
        phase: GraphIoPhase? = null,
        vertices: Long = USE_CURRENT_COUNT,
        successfulVertices: Long = USE_CURRENT_COUNT,
        edges: Long = USE_CURRENT_COUNT,
        successfulEdges: Long = USE_CURRENT_COUNT,
        skippedVertices: Long = USE_CURRENT_COUNT,
        skippedEdges: Long = USE_CURRENT_COUNT,
        failures: Long = USE_CURRENT_COUNT,
        bytesProcessed: Long? = null,
        bytesTotal: Long? = null,
        elapsed: Duration = Duration.ZERO,
    ): GraphIoProgressEvent? = lifecycleLock.withLock {
        if (state != State.STARTED) return@withLock null
        emitNonTerminal(
            type = GraphIoProgressEventType.PROGRESS,
            phase = phase,
            phaseElapsed = null,
            next = nextSnapshot(
                vertices = currentCount(vertices, snapshot.vertices),
                successfulVertices = currentCount(successfulVertices, snapshot.successfulVertices),
                edges = currentCount(edges, snapshot.edges),
                successfulEdges = currentCount(successfulEdges, snapshot.successfulEdges),
                skippedVertices = currentCount(skippedVertices, snapshot.skippedVertices),
                skippedEdges = currentCount(skippedEdges, snapshot.skippedEdges),
                failures = currentCount(failures, snapshot.failures),
                bytesProcessed = bytesProcessed,
                bytesTotal = bytesTotal,
                elapsed = currentElapsed(elapsed, snapshot.elapsed),
            ),
        )
    }

    /** phase 종료 snapshot을 발행한다. */
    fun phaseCompleted(
        phase: GraphIoPhase,
        phaseElapsed: Duration,
        vertices: Long = USE_CURRENT_COUNT,
        successfulVertices: Long = USE_CURRENT_COUNT,
        edges: Long = USE_CURRENT_COUNT,
        successfulEdges: Long = USE_CURRENT_COUNT,
        skippedVertices: Long = USE_CURRENT_COUNT,
        skippedEdges: Long = USE_CURRENT_COUNT,
        failures: Long = USE_CURRENT_COUNT,
        bytesProcessed: Long? = null,
        bytesTotal: Long? = null,
        elapsed: Duration = Duration.ZERO,
    ): GraphIoProgressEvent? = lifecycleLock.withLock {
        if (state != State.STARTED) return@withLock null
        emitNonTerminal(
            type = GraphIoProgressEventType.PHASE_COMPLETED,
            phase = phase,
            phaseElapsed = phaseElapsed,
            next = nextSnapshot(
                vertices = currentCount(vertices, snapshot.vertices),
                successfulVertices = currentCount(successfulVertices, snapshot.successfulVertices),
                edges = currentCount(edges, snapshot.edges),
                successfulEdges = currentCount(successfulEdges, snapshot.successfulEdges),
                skippedVertices = currentCount(skippedVertices, snapshot.skippedVertices),
                skippedEdges = currentCount(skippedEdges, snapshot.skippedEdges),
                failures = currentCount(failures, snapshot.failures),
                bytesProcessed = bytesProcessed,
                bytesTotal = bytesTotal,
                elapsed = currentElapsed(elapsed, snapshot.elapsed),
            ),
        )
    }

    /** 정상적으로 반환된 report의 terminal event를 발행한다. */
    fun completed(
        status: GraphIoStatus,
        vertices: Long = USE_CURRENT_COUNT,
        successfulVertices: Long = USE_CURRENT_COUNT,
        edges: Long = USE_CURRENT_COUNT,
        successfulEdges: Long = USE_CURRENT_COUNT,
        skippedVertices: Long = USE_CURRENT_COUNT,
        skippedEdges: Long = USE_CURRENT_COUNT,
        failures: Long = USE_CURRENT_COUNT,
        bytesProcessed: Long? = null,
        bytesTotal: Long? = null,
        elapsed: Duration = Duration.ZERO,
    ): GraphIoProgressEvent? = terminal(
        type = if (status == GraphIoStatus.FAILED) {
            GraphIoProgressEventType.FAILED
        } else {
            GraphIoProgressEventType.COMPLETED
        },
        status = status,
        hasStarted = true,
        vertices = vertices,
        successfulVertices = successfulVertices,
        edges = edges,
        successfulEdges = successfulEdges,
        skippedVertices = skippedVertices,
        skippedEdges = skippedEdges,
        failures = failures,
        bytesProcessed = bytesProcessed,
        bytesTotal = bytesTotal,
        elapsed = elapsed,
    )

    /** import report의 누적 수치를 progress와 terminal event로 변환한다. */
    fun completed(report: GraphImportReport): GraphIoProgressEvent? {
        if (!isStarted()) started()
        val bytesTotal = bytesProvider()
        val bytesProcessed = bytesTotal.takeUnless { report.status == GraphIoStatus.FAILED }
        emitImportPhases(report)
        progress(
            vertices = report.verticesRead,
            successfulVertices = report.verticesCreated,
            edges = report.edgesRead,
            successfulEdges = report.edgesCreated,
            skippedVertices = report.skippedVertices,
            skippedEdges = report.skippedEdges,
            failures = report.failures.size.toLong(),
            bytesProcessed = bytesProcessed,
            bytesTotal = bytesTotal,
            elapsed = report.elapsed,
        )
        return completed(
            status = report.status,
            vertices = report.verticesRead,
            successfulVertices = report.verticesCreated,
            edges = report.edgesRead,
            successfulEdges = report.edgesCreated,
            skippedVertices = report.skippedVertices,
            skippedEdges = report.skippedEdges,
            failures = report.failures.size.toLong(),
            bytesProcessed = bytesProcessed,
            bytesTotal = bytesTotal,
            elapsed = report.elapsed,
        )
    }

    /** export report의 누적 수치를 progress와 terminal event로 변환한다. */
    fun completed(report: GraphExportReport): GraphIoProgressEvent? {
        if (!isStarted()) started()
        val bytesTotal = bytesProvider()
        val bytesProcessed = bytesTotal.takeUnless { report.status == GraphIoStatus.FAILED }
        val vertices = report.verticesWritten + report.skippedVertices
        val edges = report.edgesWritten + report.skippedEdges
        emitExportPhases(report, vertices, edges)
        progress(
            vertices = vertices,
            successfulVertices = report.verticesWritten,
            edges = edges,
            successfulEdges = report.edgesWritten,
            skippedVertices = report.skippedVertices,
            skippedEdges = report.skippedEdges,
            failures = report.failures.size.toLong(),
            bytesProcessed = bytesProcessed,
            bytesTotal = bytesTotal,
            elapsed = report.elapsed,
        )
        return completed(
            status = report.status,
            vertices = vertices,
            successfulVertices = report.verticesWritten,
            edges = edges,
            successfulEdges = report.edgesWritten,
            skippedVertices = report.skippedVertices,
            skippedEdges = report.skippedEdges,
            failures = report.failures.size.toLong(),
            bytesProcessed = bytesProcessed,
            bytesTotal = bytesTotal,
            elapsed = report.elapsed,
        )
    }

    private fun emitImportPhases(report: GraphImportReport) {
        if (report.verticesRead > 0L) {
            phaseCompleted(
                phase = GraphIoPhase.CREATE_VERTEX,
                phaseElapsed = report.elapsed,
                vertices = report.verticesRead,
                successfulVertices = report.verticesCreated,
                skippedVertices = report.skippedVertices,
                edges = report.edgesRead,
                successfulEdges = report.edgesCreated,
                skippedEdges = report.skippedEdges,
                failures = report.failures.size.toLong(),
                elapsed = report.elapsed,
            )
        }
        if (report.edgesRead > 0L) {
            phaseCompleted(
                phase = GraphIoPhase.CREATE_EDGE,
                phaseElapsed = report.elapsed,
                vertices = report.verticesRead,
                successfulVertices = report.verticesCreated,
                skippedVertices = report.skippedVertices,
                edges = report.edgesRead,
                successfulEdges = report.edgesCreated,
                skippedEdges = report.skippedEdges,
                failures = report.failures.size.toLong(),
                elapsed = report.elapsed,
            )
        }
    }

    private fun emitExportPhases(report: GraphExportReport, vertices: Long, edges: Long) {
        if (vertices > 0L) {
            phaseCompleted(
                phase = GraphIoPhase.WRITE_VERTEX,
                phaseElapsed = report.elapsed,
                vertices = vertices,
                successfulVertices = report.verticesWritten,
                skippedVertices = report.skippedVertices,
                edges = edges,
                successfulEdges = report.edgesWritten,
                skippedEdges = report.skippedEdges,
                failures = report.failures.size.toLong(),
                elapsed = report.elapsed,
            )
        }
        if (edges > 0L) {
            phaseCompleted(
                phase = GraphIoPhase.WRITE_EDGE,
                phaseElapsed = report.elapsed,
                vertices = vertices,
                successfulVertices = report.verticesWritten,
                skippedVertices = report.skippedVertices,
                edges = edges,
                successfulEdges = report.edgesWritten,
                skippedEdges = report.skippedEdges,
                failures = report.failures.size.toLong(),
                elapsed = report.elapsed,
            )
        }
    }

    /** 호출자에게 전파되는 예외의 terminal event를 발행한다. */
    fun failed(
        vertices: Long = USE_CURRENT_COUNT,
        successfulVertices: Long = USE_CURRENT_COUNT,
        edges: Long = USE_CURRENT_COUNT,
        successfulEdges: Long = USE_CURRENT_COUNT,
        skippedVertices: Long = USE_CURRENT_COUNT,
        skippedEdges: Long = USE_CURRENT_COUNT,
        failures: Long = USE_CURRENT_COUNT,
        bytesProcessed: Long? = null,
        bytesTotal: Long? = null,
        elapsed: Duration = Duration.ZERO,
    ): GraphIoProgressEvent? = terminal(
        type = GraphIoProgressEventType.FAILED,
        status = GraphIoStatus.FAILED,
        hasStarted = true,
        vertices = vertices,
        successfulVertices = successfulVertices,
        edges = edges,
        successfulEdges = successfulEdges,
        skippedVertices = skippedVertices,
        skippedEdges = skippedEdges,
        failures = failures,
        bytesProcessed = bytesProcessed,
        bytesTotal = bytesTotal,
        elapsed = elapsed,
    )

    /** 취소를 한 번만 기록하며 시작 전 취소 여부를 event에 보존한다. */
    fun cancelled(
        vertices: Long = USE_CURRENT_COUNT,
        successfulVertices: Long = USE_CURRENT_COUNT,
        edges: Long = USE_CURRENT_COUNT,
        successfulEdges: Long = USE_CURRENT_COUNT,
        skippedVertices: Long = USE_CURRENT_COUNT,
        skippedEdges: Long = USE_CURRENT_COUNT,
        failures: Long = USE_CURRENT_COUNT,
        bytesProcessed: Long? = null,
        bytesTotal: Long? = null,
        elapsed: Duration = Duration.ZERO,
    ): GraphIoProgressEvent? = lifecycleLock.withLock {
        if (state == State.TERMINAL) return@withLock null
        val hasStarted = state == State.STARTED
        state = State.TERMINAL
        val next = nextSnapshot(
            vertices = currentCount(vertices, snapshot.vertices),
            successfulVertices = currentCount(successfulVertices, snapshot.successfulVertices),
            edges = currentCount(edges, snapshot.edges),
            successfulEdges = currentCount(successfulEdges, snapshot.successfulEdges),
            skippedVertices = currentCount(skippedVertices, snapshot.skippedVertices),
            skippedEdges = currentCount(skippedEdges, snapshot.skippedEdges),
            failures = currentCount(failures, snapshot.failures),
            bytesProcessed = bytesProcessed,
            bytesTotal = bytesTotal,
            elapsed = currentElapsed(elapsed, snapshot.elapsed),
        )
        snapshot = next
        dispatch(
            event(
                type = GraphIoProgressEventType.CANCELLED,
                hasStarted = hasStarted,
                status = null,
                phase = null,
                phaseElapsed = null,
                next = next,
            )
        )
    }

    fun isTerminal(): Boolean = lifecycleLock.withLock { state == State.TERMINAL }

    fun isStarted(): Boolean = lifecycleLock.withLock { state == State.STARTED }

    /** 동기 작업을 lifecycle로 감싼다. */
    fun <T> run(block: () -> T, onCompleted: (T) -> Unit): T {
        return try {
            started()
            val result = block()
            onCompleted(result)
            result
        } catch (error: CancellationException) {
            emitTerminalSafely(error) { cancelled() }
            rethrow(error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            emitTerminalSafely(error) { cancelled() }
            rethrow(error)
        } catch (error: Throwable) {
            emitTerminalSafely(error) { failed() }
            rethrow(error)
        }
    }

    /** suspend 작업을 lifecycle로 감싼다. */
    suspend fun <T> runSuspending(
        block: suspend () -> T,
        onCompleted: (T) -> Unit,
    ): T {
        return try {
            started()
            val result = block()
            onCompleted(result)
            result
        } catch (error: CancellationException) {
            emitTerminalSafely(error) { cancelled() }
            rethrow(error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            emitTerminalSafely(error) { cancelled() }
            rethrow(error)
        } catch (error: Throwable) {
            emitTerminalSafely(error) { failed() }
            rethrow(error)
        }
    }

    private fun emitTerminalSafely(primary: Throwable, terminal: () -> Unit) {
        try {
            terminal()
        } catch (listenerError: Error) {
            if (listenerError !== primary) {
                primary.addSuppressed(listenerError)
            }
        }
    }

    private fun rethrow(error: Throwable): Nothing = throw error

    private fun terminal(
        type: GraphIoProgressEventType,
        status: GraphIoStatus,
        hasStarted: Boolean,
        vertices: Long,
        successfulVertices: Long,
        edges: Long,
        successfulEdges: Long,
        skippedVertices: Long,
        skippedEdges: Long,
        failures: Long,
        bytesProcessed: Long?,
        bytesTotal: Long?,
        elapsed: Duration,
    ): GraphIoProgressEvent? = lifecycleLock.withLock {
        if (state == State.TERMINAL) return@withLock null
        if (state == State.NEW) {
            state = State.STARTED
        }
        val next = nextSnapshot(
            vertices = currentCount(vertices, snapshot.vertices),
            successfulVertices = currentCount(successfulVertices, snapshot.successfulVertices),
            edges = currentCount(edges, snapshot.edges),
            successfulEdges = currentCount(successfulEdges, snapshot.successfulEdges),
            skippedVertices = currentCount(skippedVertices, snapshot.skippedVertices),
            skippedEdges = currentCount(skippedEdges, snapshot.skippedEdges),
            failures = currentCount(failures, snapshot.failures),
            bytesProcessed = bytesProcessed,
            bytesTotal = bytesTotal,
            elapsed = currentElapsed(elapsed, snapshot.elapsed),
        )
        state = State.TERMINAL
        snapshot = next
        dispatch(
            event(
                type = type,
                hasStarted = hasStarted,
                status = status,
                phase = null,
                phaseElapsed = null,
                next = next,
            )
        )
    }

    private fun emitNonTerminal(
        type: GraphIoProgressEventType,
        phase: GraphIoPhase?,
        phaseElapsed: Duration?,
        next: Snapshot,
    ): GraphIoProgressEvent {
        snapshot = next
        return dispatch(
            event(
                type = type,
                hasStarted = true,
                status = null,
                phase = phase,
                phaseElapsed = phaseElapsed,
                next = next,
            )
        )
    }

    private fun dispatch(event: GraphIoProgressEvent): GraphIoProgressEvent {
        if (listener === GraphIoProgressListener.NOOP) return event

        when (listener) {
            is GraphIoCompositeProgressListener -> {
                listener.dispatch(
                    event = event,
                    onException = ::warnListenerFailure,
                    rethrowExceptions = false,
                )
            }

            else -> {
                try {
                    listener.onEvent(event)
                } catch (error: Exception) {
                    warnListenerFailure()
                }
            }
        }
        return event
    }

    private fun warnListenerFailure() {
        log.warn("Graph I/O progress listener callback failed; callback was isolated")
    }

    /** 시작 callback에서 [Error]가 발생해도 bridge가 active 상태에 남지 않도록 terminalize한다. */
    private fun terminalAfterListenerError(primary: Error) {
        if (state == State.TERMINAL) return
        val next = snapshot
        state = State.TERMINAL
        snapshot = next
        try {
            dispatch(
                event(
                    type = GraphIoProgressEventType.FAILED,
                    hasStarted = true,
                    status = GraphIoStatus.FAILED,
                    phase = null,
                    phaseElapsed = null,
                    next = next,
                )
            )
        } catch (terminalError: Error) {
            if (terminalError !== primary) primary.addSuppressed(terminalError)
        }
    }

    private fun event(
        type: GraphIoProgressEventType,
        hasStarted: Boolean,
        status: GraphIoStatus?,
        phase: GraphIoPhase?,
        phaseElapsed: Duration?,
        next: Snapshot,
    ): GraphIoProgressEvent = GraphIoProgressEvent(
        runId = runId,
        hasStarted = hasStarted,
        type = type,
        operation = operation,
        format = format,
        phase = phase,
        status = status,
        vertices = next.vertices,
        successfulVertices = next.successfulVertices,
        edges = next.edges,
        successfulEdges = next.successfulEdges,
        skippedVertices = next.skippedVertices,
        skippedEdges = next.skippedEdges,
        failures = next.failures,
        bytesProcessed = next.bytesProcessed,
        bytesTotal = next.bytesTotal,
        elapsed = next.elapsed,
        phaseElapsed = phaseElapsed,
    )

    private fun nextSnapshot(
        vertices: Long,
        successfulVertices: Long,
        edges: Long,
        successfulEdges: Long,
        skippedVertices: Long,
        skippedEdges: Long,
        failures: Long,
        bytesProcessed: Long?,
        bytesTotal: Long?,
        elapsed: Duration,
    ): Snapshot {
        val next = Snapshot(
            vertices = vertices,
            successfulVertices = successfulVertices,
            edges = edges,
            successfulEdges = successfulEdges,
            skippedVertices = skippedVertices,
            skippedEdges = skippedEdges,
            failures = failures,
            bytesProcessed = bytesProcessed ?: snapshot.bytesProcessed,
            bytesTotal = bytesTotal ?: snapshot.bytesTotal,
            elapsed = elapsed,
        )
        require(next.vertices >= snapshot.vertices) { "vertices must be monotonic" }
        require(next.successfulVertices >= snapshot.successfulVertices) { "successfulVertices must be monotonic" }
        require(next.edges >= snapshot.edges) { "edges must be monotonic" }
        require(next.successfulEdges >= snapshot.successfulEdges) { "successfulEdges must be monotonic" }
        require(next.skippedVertices >= snapshot.skippedVertices) { "skippedVertices must be monotonic" }
        require(next.skippedEdges >= snapshot.skippedEdges) { "skippedEdges must be monotonic" }
        require(next.failures >= snapshot.failures) { "failures must be monotonic" }
        val previousBytesProcessed = snapshot.bytesProcessed
        val previousBytesTotal = snapshot.bytesTotal
        require(
            next.bytesProcessed == null ||
                previousBytesProcessed == null ||
                next.bytesProcessed >= previousBytesProcessed
        ) {
            "bytesProcessed must be monotonic"
        }
        require(next.bytesTotal == null || previousBytesTotal == null || next.bytesTotal >= previousBytesTotal) {
            "bytesTotal must be monotonic"
        }
        require(!next.elapsed.isNegative && next.elapsed >= snapshot.elapsed) { "elapsed must be monotonic" }
        return next
    }

    private fun currentCount(value: Long, previous: Long): Long =
        if (value == USE_CURRENT_COUNT) previous else value

    private fun currentElapsed(value: Duration, previous: Duration): Duration =
        if (value.isZero && !previous.isZero) previous else value

    companion object : KLogging() {
        private const val USE_CURRENT_COUNT: Long = Long.MIN_VALUE
        private val RUN_IDS = AtomicLong(0L)
    }
}

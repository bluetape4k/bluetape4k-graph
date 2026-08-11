package io.bluetape4k.graph.io.report

import java.io.Serializable
import java.time.Duration

/**
 * 그래프 벌크 I/O의 비밀값 없는 진행 이벤트.
 *
 * 정점·간선 수는 import에서는 관찰된 입력 수, export에서는 처리된 입력 수이며,
 * `successful*` 수는 실제 생성/기록된 수다. listener는 작업 thread에서 동기
 * 호출되므로 callback에서 blocking 작업을 수행하지 않아야 한다.
 */
data class GraphIoProgressEvent(
    val runId: Long,
    val hasStarted: Boolean = true,
    val type: GraphIoProgressEventType,
    val operation: GraphIoOperation,
    val format: GraphIoFormat,
    val phase: GraphIoPhase? = null,
    val status: GraphIoStatus? = null,
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
    val phaseElapsed: Duration? = null,
) : Serializable {

    init {
        require(runId >= 0L) { "runId must be >= 0" }
        require(vertices >= 0L) { "vertices must be >= 0" }
        require(successfulVertices >= 0L) { "successfulVertices must be >= 0" }
        require(edges >= 0L) { "edges must be >= 0" }
        require(successfulEdges >= 0L) { "successfulEdges must be >= 0" }
        require(skippedVertices >= 0L) { "skippedVertices must be >= 0" }
        require(skippedEdges >= 0L) { "skippedEdges must be >= 0" }
        require(failures >= 0L) { "failures must be >= 0" }
        require(skippedVertices <= vertices && successfulVertices <= vertices - skippedVertices) {
            "successfulVertices + skippedVertices must be <= vertices"
        }
        require(skippedEdges <= edges && successfulEdges <= edges - skippedEdges) {
            "successfulEdges + skippedEdges must be <= edges"
        }
        require(bytesProcessed == null || bytesProcessed >= 0L) {
            "bytesProcessed must be >= 0"
        }
        require(bytesTotal == null || bytesTotal >= 0L) {
            "bytesTotal must be >= 0"
        }
        require(bytesProcessed == null || bytesTotal == null || bytesProcessed <= bytesTotal) {
            "bytesProcessed must be <= bytesTotal"
        }
        require(!elapsed.isNegative) { "elapsed must be >= 0" }
        require(phaseElapsed == null || !phaseElapsed.isNegative) {
            "phaseElapsed must be >= 0"
        }

        when (type) {
            GraphIoProgressEventType.STARTED -> {
                require(hasStarted) { "STARTED event must have hasStarted=true" }
                require(status == null) { "STARTED event must not have status" }
                require(phase == null) { "STARTED event must not have phase" }
                require(elapsed.isZero) { "STARTED event elapsed must be zero" }
                require(phaseElapsed == null) { "STARTED event must not have phaseElapsed" }
            }

            GraphIoProgressEventType.PROGRESS -> {
                require(hasStarted) { "PROGRESS event must have hasStarted=true" }
                require(status == null) { "PROGRESS event must not have status" }
            }

            GraphIoProgressEventType.PHASE_COMPLETED -> {
                require(hasStarted) { "PHASE_COMPLETED event must have hasStarted=true" }
                require(status == null) { "PHASE_COMPLETED event must not have status" }
                requireNotNull(phase) { "PHASE_COMPLETED event requires phase" }
                requireNotNull(phaseElapsed) { "PHASE_COMPLETED event requires phaseElapsed" }
            }

            GraphIoProgressEventType.COMPLETED,
            GraphIoProgressEventType.FAILED,
            -> {
                require(hasStarted) { "terminal event must have hasStarted=true" }
                requireNotNull(status) { "${type.name} event requires status" }
                require(phase == null) { "terminal event must not have phase" }
                require(phaseElapsed == null) { "terminal event must not have phaseElapsed" }
            }

            GraphIoProgressEventType.CANCELLED -> {
                require(status == null) { "CANCELLED event must not have status" }
                require(phase == null) { "CANCELLED event must not have phase" }
                require(phaseElapsed == null) { "CANCELLED event must not have phaseElapsed" }
                if (!hasStarted) {
                    require(runId > 0L) { "pre-start cancellation requires a positive runId" }
                }
            }
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

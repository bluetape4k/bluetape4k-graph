package io.bluetape4k.graph.io.micrometer

import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoProgressEvent
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * graph-io 진행 이벤트를 Micrometer meter로 변환한다.
 *
 * tag는 operation/format/status/kind/phase의 고정 enum 값만 사용한다. source
 * 경로, label, record id, run id, 예외 정보는 meter에 기록하지 않는다.
 */
class GraphIoMicrometerProgressListener(
    private val registry: MeterRegistry,
) : GraphIoProgressListener {

    private val activeCells = Array(GraphIoOperation.entries.size * GraphIoFormat.entries.size) { AtomicLong() }

    init {
        GraphIoOperation.entries.forEach { operation ->
            GraphIoFormat.entries.forEach { format ->
                val cell = activeCells[cellIndex(operation, format)]
                Gauge.builder(METER_ACTIVE) { cell.get().toDouble() }
                    .description("Active graph-io runs")
                    .tags(operationFormatTags(operation, format))
                    .register(registry)
            }
        }
    }

    override fun onEvent(event: GraphIoProgressEvent) {
        if (event.runId == 0L) return

        when (event.type) {
            GraphIoProgressEventType.STARTED -> active(event).incrementAndGet()
            GraphIoProgressEventType.PHASE_COMPLETED -> {
                val phase = event.phase
                val elapsed = event.phaseElapsed
                if (phase != null && elapsed != null) {
                    Timer.builder(METER_PHASE_DURATION)
                        .tags(operationFormatTags(event.operation, event.format) + Tag.of("phase", phaseTag(phase)))
                        .register(registry)
                        .record(elapsed)
                }
            }

            GraphIoProgressEventType.COMPLETED,
            GraphIoProgressEventType.FAILED,
            GraphIoProgressEventType.CANCELLED,
            -> recordTerminal(event)

            GraphIoProgressEventType.PROGRESS -> Unit
        }
    }

    private fun recordTerminal(event: GraphIoProgressEvent) {
        if (event.hasStarted) {
            active(event).updateAndGet { value -> if (value > 0L) value - 1L else 0L }
        }

        val status = statusTag(event)
        Counter.builder(METER_RUNS)
            .tags(operationFormatTags(event.operation, event.format) + Tag.of("status", status))
            .register(registry)
            .increment()
        Timer.builder(METER_DURATION)
            .tags(operationFormatTags(event.operation, event.format) + Tag.of("status", status))
            .register(registry)
            .record(event.elapsed)

        recordCounter(METER_RECORDS, event, "vertices", event.successfulVertices)
        recordCounter(METER_RECORDS, event, "edges", event.successfulEdges)
        recordCounter(METER_RECORDS, event, "skipped_vertices", event.skippedVertices)
        recordCounter(METER_RECORDS, event, "skipped_edges", event.skippedEdges)
        recordCounter(METER_RECORDS, event, "failures", event.failures)
        event.bytesProcessed?.takeIf { it > 0L }?.let { bytes ->
            Counter.builder(METER_BYTES)
                .tags(operationFormatTags(event.operation, event.format))
                .register(registry)
                .increment(bytes.toDouble())
        }
    }

    private fun recordCounter(name: String, event: GraphIoProgressEvent, kind: String, value: Long) {
        if (value <= 0L) return
        Counter.builder(name)
            .tags(operationFormatTags(event.operation, event.format) + Tag.of("kind", kind))
            .register(registry)
            .increment(value.toDouble())
    }

    private fun active(event: GraphIoProgressEvent): AtomicLong =
        activeCells[cellIndex(event.operation, event.format)]

    private fun statusTag(event: GraphIoProgressEvent): String = when (event.type) {
        GraphIoProgressEventType.CANCELLED -> "cancelled"
        else -> requireNotNull(event.status).name.lowercase(Locale.ROOT)
    }

    private fun operationTag(operation: GraphIoOperation): String = operation.name.lowercase(Locale.ROOT)

    private fun formatTag(format: GraphIoFormat): String = format.name.lowercase(Locale.ROOT)

    private fun operationFormatTags(operation: GraphIoOperation, format: GraphIoFormat): List<Tag> =
        listOf(
            Tag.of("operation", operationTag(operation)),
            Tag.of("format", formatTag(format)),
        )

    private fun phaseTag(phase: GraphIoPhase): String =
        phase.name.lowercase(Locale.ROOT)

    private fun cellIndex(operation: GraphIoOperation, format: GraphIoFormat): Int =
        operation.ordinal * GraphIoFormat.entries.size + format.ordinal

    companion object {
        const val METER_RUNS: String = "graph.io.runs"
        const val METER_RECORDS: String = "graph.io.records"
        const val METER_BYTES: String = "graph.io.bytes"
        const val METER_DURATION: String = "graph.io.duration"
        const val METER_PHASE_DURATION: String = "graph.io.phase.duration"
        const val METER_ACTIVE: String = "graph.io.active"
    }
}

package io.bluetape4k.graph.io.micrometer

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoProgressEvent
import io.bluetape4k.graph.io.report.GraphIoProgressEventType
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Duration

class GraphIoMicrometerProgressListenerTest {

    @Test
    fun `terminal event maps fixed counters timers and active gauge`() {
        val registry = SimpleMeterRegistry()
        val listener = GraphIoMicrometerProgressListener(registry)
        val started = event(GraphIoProgressEventType.STARTED)
        listener.onEvent(started)
        listener.onEvent(
            event(
                type = GraphIoProgressEventType.PHASE_COMPLETED,
                phase = GraphIoPhase.CREATE_VERTEX,
                phaseElapsed = Duration.ofMillis(7),
            )
        )
        listener.onEvent(
            event(
                type = GraphIoProgressEventType.COMPLETED,
                status = GraphIoStatus.PARTIAL,
                successfulVertices = 3,
                successfulEdges = 2,
                skippedVertices = 1,
                skippedEdges = 1,
                failures = 2,
                bytesProcessed = 128,
                elapsed = Duration.ofMillis(20),
            )
        )

        registry.get(GraphIoMicrometerProgressListener.METER_RUNS)
            .tag("operation", "import")
            .tag("format", "csv")
            .tag("status", "partial")
            .counter().count() shouldBeEqualTo 1.0
        registry.get(GraphIoMicrometerProgressListener.METER_RECORDS)
            .tag("operation", "import")
            .tag("format", "csv")
            .tag("kind", "vertices")
            .counter().count() shouldBeEqualTo 3.0
        registry.get(GraphIoMicrometerProgressListener.METER_BYTES)
            .tag("operation", "import")
            .tag("format", "csv")
            .counter().count() shouldBeEqualTo 128.0
        registry.get(GraphIoMicrometerProgressListener.METER_DURATION)
            .tag("operation", "import")
            .tag("format", "csv")
            .tag("status", "partial")
            .timer().count() shouldBeEqualTo 1L
        registry.get(GraphIoMicrometerProgressListener.METER_PHASE_DURATION)
            .tag("operation", "import")
            .tag("format", "csv")
            .tag("phase", "create_vertex")
            .timer().count() shouldBeEqualTo 1L
        registry.get(GraphIoMicrometerProgressListener.METER_ACTIVE)
            .tag("operation", "import")
            .tag("format", "csv")
            .gauge().value() shouldBeEqualTo 0.0
    }

    @Test
    fun `run zero and duplicate terminal do not create unbounded tags or negative active`() {
        val registry = SimpleMeterRegistry()
        val listener = GraphIoMicrometerProgressListener(registry)
        listener.onEvent(event(GraphIoProgressEventType.PROGRESS, runId = 0L))
        listener.onEvent(event(GraphIoProgressEventType.CANCELLED, hasStarted = true))
        listener.onEvent(event(GraphIoProgressEventType.CANCELLED, hasStarted = true))

        registry.get(GraphIoMicrometerProgressListener.METER_RUNS)
            .tag("operation", "import")
            .tag("format", "csv")
            .tag("status", "cancelled")
            .counter().count() shouldBeEqualTo 2.0
        registry.get(GraphIoMicrometerProgressListener.METER_ACTIVE)
            .tag("operation", "import")
            .tag("format", "csv")
            .gauge().value() shouldBeEqualTo 0.0
    }

    private fun event(
        type: GraphIoProgressEventType,
        runId: Long = 1L,
        hasStarted: Boolean = true,
        status: GraphIoStatus? = null,
        phase: GraphIoPhase? = null,
        phaseElapsed: Duration? = null,
        successfulVertices: Long = 0,
        successfulEdges: Long = 0,
        skippedVertices: Long = 0,
        skippedEdges: Long = 0,
        failures: Long = 0,
        bytesProcessed: Long? = null,
        elapsed: Duration = Duration.ZERO,
    ) = GraphIoProgressEvent(
        runId = runId,
        hasStarted = hasStarted,
        type = type,
        operation = GraphIoOperation.IMPORT,
        format = GraphIoFormat.CSV,
        phase = phase,
        status = status,
        vertices = successfulVertices + skippedVertices,
        successfulVertices = successfulVertices,
        edges = successfulEdges + skippedEdges,
        successfulEdges = successfulEdges,
        skippedVertices = skippedVertices,
        skippedEdges = skippedEdges,
        failures = failures,
        bytesProcessed = bytesProcessed,
        elapsed = elapsed,
        phaseElapsed = phaseElapsed,
    )
}

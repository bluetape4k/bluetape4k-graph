package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Duration

class GraphIoProgressEventTest {

    @Test
    fun `event is serializable and retains aggregate snapshot`() {
        val original = GraphIoProgressEvent(
            runId = 7L,
            type = GraphIoProgressEventType.PHASE_COMPLETED,
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.CSV,
            phase = GraphIoPhase.CREATE_VERTEX,
            vertices = 10L,
            successfulVertices = 8L,
            skippedVertices = 2L,
            failures = 1L,
            bytesProcessed = 128L,
            bytesTotal = 256L,
            elapsed = Duration.ofMillis(20),
            phaseElapsed = Duration.ofMillis(5),
        )

        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(original) }
        val copy = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }

        copy shouldBeEqualTo original
    }

    @Test
    fun `event defaults optional status and phase values to null`() {
        val event = GraphIoProgressEvent(
            runId = 1L,
            type = GraphIoProgressEventType.PROGRESS,
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
        )

        event.status.shouldBeNull()
        event.phase.shouldBeNull()
        event.bytesProcessed.shouldBeNull()
        event.bytesTotal.shouldBeNull()
    }

    @Test
    fun `event rejects negative and inconsistent aggregates`() {
        assertFailsWith<IllegalArgumentException> {
            GraphIoProgressEvent(
                runId = 1L,
                type = GraphIoProgressEventType.PROGRESS,
                operation = GraphIoOperation.IMPORT,
                format = GraphIoFormat.CSV,
                vertices = -1L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphIoProgressEvent(
                runId = 1L,
                type = GraphIoProgressEventType.PROGRESS,
                operation = GraphIoOperation.IMPORT,
                format = GraphIoFormat.CSV,
                vertices = 1L,
                successfulVertices = 2L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphIoProgressEvent(
                runId = 1L,
                type = GraphIoProgressEventType.PROGRESS,
                operation = GraphIoOperation.IMPORT,
                format = GraphIoFormat.CSV,
                bytesProcessed = 2L,
                bytesTotal = 1L,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphIoProgressEvent(
                runId = 1L,
                type = GraphIoProgressEventType.PROGRESS,
                operation = GraphIoOperation.IMPORT,
                format = GraphIoFormat.CSV,
                vertices = Long.MAX_VALUE,
                successfulVertices = Long.MAX_VALUE,
                skippedVertices = 1L,
            )
        }
    }

    @Test
    fun `terminal event requires status except cancellation`() {
        val completed = GraphIoProgressEvent(
            runId = 2L,
            type = GraphIoProgressEventType.COMPLETED,
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.NDJSON_JACKSON2,
            status = GraphIoStatus.PARTIAL,
        )
        completed.status shouldBeEqualTo GraphIoStatus.PARTIAL

        assertFailsWith<IllegalArgumentException> {
            GraphIoProgressEvent(
                runId = 2L,
                type = GraphIoProgressEventType.COMPLETED,
                operation = GraphIoOperation.EXPORT,
                format = GraphIoFormat.NDJSON_JACKSON2,
            )
        }

        val cancelled = GraphIoProgressEvent(
            runId = 2L,
            type = GraphIoProgressEventType.CANCELLED,
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.NDJSON_JACKSON2,
        )
        cancelled.status.shouldBeNull()
    }
}

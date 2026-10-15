package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.javatimes.millis
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphIoProgressEventTest {

    companion object: KLogging()

    private val original = GraphIoProgressEvent(
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
        elapsed = 20.millis(),
        phaseElapsed = 5.millis(),
    )

    @Test
    fun `event is java serializable and retains aggregate snapshot`() {
        val serializer = JdkBinarySerializer(objectInputFilter = null)
        val bytes = serializer.serialize(original)
        val restored = serializer.deserialize<GraphIoProgressEvent>(bytes)
        log.debug { "restored=$restored" }
        restored shouldBeEqualTo original
    }

    @Test
    fun `event is fory serializable and retains aggregate snapshot`() {
        val serializer = BinarySerializers.FastFory
        val bytes = serializer.serialize(original)
        val restored = serializer.deserialize<GraphIoProgressEvent>(bytes)
        log.debug { "restored=$restored" }
        restored shouldBeEqualTo original
    }

    @Test
    fun `event defaults optional status and phase values to null`() {
        val event = GraphIoProgressEvent(
            runId = 1L,
            type = GraphIoProgressEventType.PROGRESS,
            operation = GraphIoOperation.EXPORT,
            format = GraphIoFormat.GRAPHML,
        )

        log.debug { "event=$event" }
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

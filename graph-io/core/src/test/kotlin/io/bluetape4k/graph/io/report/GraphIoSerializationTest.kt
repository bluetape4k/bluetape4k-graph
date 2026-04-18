package io.bluetape4k.graph.io.report

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Duration

class GraphIoSerializationTest {

    private fun <T> roundTrip(obj: T): T {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { it.writeObject(obj) }
        @Suppress("UNCHECKED_CAST")
        return ObjectInputStream(ByteArrayInputStream(baos.toByteArray())).use { it.readObject() } as T
    }

    @Test
    fun `GraphIoFailure survives Java serialization`() {
        val original = GraphIoFailure(
            phase = GraphIoPhase.WRITE_VERTEX,
            severity = GraphIoFailureSeverity.INFO,
            message = "test failure",
            recordId = "rec-42",
        )
        val deserialized = roundTrip(original)
        deserialized shouldBeEqualTo original
    }

    @Test
    fun `GraphImportReport survives Java serialization`() {
        val original = GraphImportReport(
            status = GraphIoStatus.COMPLETED,
            format = GraphIoFormat.CSV,
            verticesRead = 10L,
            verticesCreated = 10L,
            edgesRead = 5L,
            edgesCreated = 5L,
            skippedVertices = 0L,
            skippedEdges = 0L,
            elapsed = Duration.ofSeconds(1),
        )
        val deserialized = roundTrip(original)
        deserialized shouldBeEqualTo original
    }

    @Test
    fun `GraphExportReport survives Java serialization`() {
        val original = GraphExportReport(
            status = GraphIoStatus.FAILED,
            format = GraphIoFormat.NDJSON_JACKSON3,
            verticesWritten = 0L,
            edgesWritten = 0L,
            elapsed = Duration.ofMillis(50),
            failures = listOf(
                GraphIoFailure(phase = GraphIoPhase.WRITE_EDGE, message = "connection lost"),
            ),
        )
        val deserialized = roundTrip(original)
        deserialized shouldBeEqualTo original
        deserialized.failures[0].message shouldBeEqualTo "connection lost"
    }
}

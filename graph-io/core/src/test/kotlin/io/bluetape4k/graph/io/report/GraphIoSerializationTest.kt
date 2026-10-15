package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.javatimes.seconds
import org.junit.jupiter.api.Test

class GraphIoSerializationTest {

    private fun <T: Any> jdkRoundtrip(origin: T): T? {
        val serializer = JdkBinarySerializer(objectInputFilter = null)
        val bytes = serializer.serialize(origin)
        return serializer.deserialize(bytes)
    }

    private fun <T: Any> foryRoundTrip(origin: T): T? {
        val bytes = BinarySerializers.FastFory.serialize(origin)
        return BinarySerializers.FastFory.deserialize(bytes)
    }

    @Test
    fun `GraphIoFailure survives Java serialization`() {
        val original = GraphIoFailure(
            phase = GraphIoPhase.WRITE_VERTEX,
            severity = GraphIoFailureSeverity.INFO,
            message = "test failure",
            recordId = "rec-42",
        )
        val jdkRestored = jdkRoundtrip(original).shouldNotBeNull()
        jdkRestored shouldBeEqualTo original

        val foryRestored = foryRoundTrip(original).shouldNotBeNull()
        foryRestored shouldBeEqualTo original
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
            elapsed = 1.seconds(),
        )
        val jdkRestored = jdkRoundtrip(original).shouldNotBeNull()
        jdkRestored shouldBeEqualTo original

        val foryRestored = foryRoundTrip(original).shouldNotBeNull()
        foryRestored shouldBeEqualTo original
    }

    @Test
    fun `GraphExportReport survives Java serialization`() {
        val original = GraphExportReport(
            status = GraphIoStatus.FAILED,
            format = GraphIoFormat.NDJSON_JACKSON3,
            verticesWritten = 0L,
            edgesWritten = 0L,
            elapsed = 50.seconds(),
            failures = listOf(
                GraphIoFailure(phase = GraphIoPhase.WRITE_EDGE, message = "connection lost"),
            ),
        )
        val jdkRestored = jdkRoundtrip(original).shouldNotBeNull()
        jdkRestored shouldBeEqualTo original
        jdkRestored.failures.single().message shouldBeEqualTo "connection lost"

        val foryRestored = foryRoundTrip(original).shouldNotBeNull()
        foryRestored shouldBeEqualTo original
        foryRestored.failures.single().message shouldBeEqualTo "connection lost"
    }
}

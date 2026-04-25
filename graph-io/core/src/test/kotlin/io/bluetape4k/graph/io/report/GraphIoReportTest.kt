package io.bluetape4k.graph.io.report

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Duration

class GraphIoReportTest {

    @Test
    fun `GraphIoFailure requires non-blank message`() {
        { GraphIoFailure(phase = GraphIoPhase.READ_VERTEX, message = " ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `GraphIoFailure defaults to ERROR severity`() {
        val failure = GraphIoFailure(phase = GraphIoPhase.CREATE_EDGE, message = "edge creation failed")
        failure.severity shouldBeEqualTo GraphIoFailureSeverity.ERROR
    }

    @Test
    fun `GraphImportReport holds all fields correctly`() {
        val report = GraphImportReport(
            status = GraphIoStatus.COMPLETED,
            format = GraphIoFormat.CSV,
            verticesRead = 100L,
            verticesCreated = 98L,
            edgesRead = 200L,
            edgesCreated = 195L,
            skippedVertices = 2L,
            skippedEdges = 5L,
            elapsed = Duration.ofSeconds(3),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesRead shouldBeEqualTo 100L
        report.failures.isEmpty().shouldBeTrue()
    }

    @Test
    fun `GraphExportReport defaults skipped counts to zero`() {
        val report = GraphExportReport(
            status = GraphIoStatus.COMPLETED,
            format = GraphIoFormat.NDJSON_JACKSON2,
            verticesWritten = 50L,
            edgesWritten = 80L,
            elapsed = Duration.ofMillis(500),
        )
        report.skippedVertices shouldBeEqualTo 0L
        report.skippedEdges shouldBeEqualTo 0L
    }

    @Test
    fun `GraphImportReport with failures reflects PARTIAL status`() {
        val failure = GraphIoFailure(
            phase = GraphIoPhase.READ_EDGE,
            severity = GraphIoFailureSeverity.WARN,
            message = "skipped malformed edge",
        )
        val report = GraphImportReport(
            status = GraphIoStatus.PARTIAL,
            format = GraphIoFormat.GRAPHML,
            verticesRead = 10L,
            verticesCreated = 10L,
            edgesRead = 5L,
            edgesCreated = 4L,
            skippedVertices = 0L,
            skippedEdges = 1L,
            elapsed = Duration.ofMillis(100),
            failures = listOf(failure),
        )
        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.failures.size shouldBeEqualTo 1
        report.failures[0].severity shouldBeEqualTo GraphIoFailureSeverity.WARN
    }
}

package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.javatimes.millis
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.Duration

class GraphIoReportTest {

    companion object: KLogging()

    @Test
    fun `GraphIoFailure requires non-blank message`() {
        assertFailsWith<IllegalArgumentException> {
            GraphIoFailure(phase = GraphIoPhase.READ_VERTEX, message = " ")
        }
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
            elapsed = 3.seconds(),
        )
        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.format shouldBeEqualTo GraphIoFormat.CSV
        report.verticesRead shouldBeEqualTo 100L
        report.verticesCreated shouldBeEqualTo 98L
        report.edgesRead shouldBeEqualTo 200L
        report.edgesCreated shouldBeEqualTo 195L
        report.skippedVertices shouldBeEqualTo 2L
        report.skippedEdges shouldBeEqualTo 5L
        report.elapsed shouldBeEqualTo 3.seconds()
        report.failures.shouldBeEmpty()
    }

    @Test
    fun `GraphExportReport defaults skipped counts to zero`() {
        val report = GraphExportReport(
            status = GraphIoStatus.COMPLETED,
            format = GraphIoFormat.NDJSON_JACKSON2,
            verticesWritten = 50L,
            edgesWritten = 80L,
            elapsed = 500.millis(),
        )
        log.debug { "report=$report" }
        report.skippedVertices shouldBeEqualTo 0L
        report.skippedEdges shouldBeEqualTo 0L
    }

    @Test
    fun `GraphImportReport rejects negative counts`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportReport(GraphIoStatus.COMPLETED, GraphIoFormat.CSV, -1L, 0L, 0L, 0L, elapsed = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphImportReport(GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, -1L, 0L, 0L, elapsed = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphImportReport(GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, 0L, -1L, 0L, elapsed = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphImportReport(GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, 0L, 0L, -1L, elapsed = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphImportReport(
                GraphIoStatus.COMPLETED,
                GraphIoFormat.CSV,
                0L,
                0L,
                0L,
                0L,
                skippedVertices = -1L,
                elapsed = Duration.ZERO,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphImportReport(
                GraphIoStatus.COMPLETED,
                GraphIoFormat.CSV,
                0L,
                0L,
                0L,
                0L,
                skippedEdges = -1L,
                elapsed = Duration.ZERO,
            )
        }
    }

    @Test
    fun `GraphExportReport rejects negative counts`() {
        assertFailsWith<IllegalArgumentException> {
            GraphExportReport(GraphIoStatus.COMPLETED, GraphIoFormat.CSV, -1L, 0L, elapsed = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphExportReport(GraphIoStatus.COMPLETED, GraphIoFormat.CSV, 0L, -1L, elapsed = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphExportReport(
                GraphIoStatus.COMPLETED,
                GraphIoFormat.CSV,
                0L,
                0L,
                skippedVertices = -1L,
                elapsed = Duration.ZERO,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GraphExportReport(
                GraphIoStatus.COMPLETED,
                GraphIoFormat.CSV,
                0L,
                0L,
                skippedEdges = -1L,
                elapsed = Duration.ZERO,
            )
        }
    }

    @Test
    fun `GraphImportReport defaults skipped counts to zero`() {
        val report = GraphImportReport(
            status = GraphIoStatus.COMPLETED,
            format = GraphIoFormat.CSV,
            verticesRead = 10L,
            verticesCreated = 10L,
            edgesRead = 5L,
            edgesCreated = 5L,
            elapsed = 100.millis(),
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
            elapsed = 100.millis(),
            failures = listOf(failure),
        )
        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
    }
}

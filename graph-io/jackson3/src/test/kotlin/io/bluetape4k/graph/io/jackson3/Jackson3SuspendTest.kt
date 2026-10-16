package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Jackson3SuspendTest {

    companion object: KLoggingChannel()

    @Test
    fun `suspend round trip`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphSuspendOperations()
        val alice = src.createVertex("Person", "name" to "Alice")
        val bob = src.createVertex("Person", "name" to "Bob")
        src.createEdge(alice.id, bob.id, "KNOWS")

        val exportReport = SuspendJackson3NdJsonBulkExporter.Default
            .exportGraphSuspending(
                out.asSink(),
                src,
                GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
            )
        log.debug { "exportReport: $exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = SuspendJackson3NdJsonBulkImporter()
            .importGraphSuspending(
                out.asSource(),
                TinkerGraphSuspendOperations(),
                GraphImportOptions(),
            )
        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `suspend importer reports invalid vertex envelope`(@TempDir dir: Path) = runTest {
        val input = dir.resolve("invalid-vertex.ndjson")
        Files.writeString(input, """{"type":"vertex","label":"Person"}""" + "\n")

        val report = SuspendJackson3NdJsonBulkImporter()
            .importGraphSuspending(
                input.asSource(),
                TinkerGraphSuspendOperations(),
                GraphImportOptions(),
            )

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.single().location shouldBeEqualTo "line:1"
        report.failures.single().message shouldContain "missing id"
    }
}

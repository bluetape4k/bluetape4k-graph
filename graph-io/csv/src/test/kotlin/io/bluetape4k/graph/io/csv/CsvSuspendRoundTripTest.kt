package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CsvSuspendRoundTripTest {

    companion object: KLoggingChannel()

    @Test
    fun `suspend round trip two vertices and one edge`(@TempDir dir: Path) = runTest {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val sourceOps = TinkerGraphSuspendOperations()
        val alice = sourceOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = sourceOps.createVertex("Person", mapOf("name" to "Bob"))
        sourceOps.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2024"))

        val exporter = SuspendCsvGraphBulkExporter()
        exporter.exportGraphSuspending(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            sourceOps,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val targetOps = TinkerGraphSuspendOperations()
        val importer = SuspendCsvGraphBulkImporter()
        val report = importer.importGraphSuspending(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            targetOps,
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

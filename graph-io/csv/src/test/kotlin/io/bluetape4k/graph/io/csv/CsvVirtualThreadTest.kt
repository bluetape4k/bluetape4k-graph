package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CsvVirtualThreadTest {

    companion object: KLoggingChannel()

    @Test
    fun `virtual thread round trip two vertices and one edge`(@TempDir dir: Path) {
        val vOut = dir.resolve("v.csv")
        val eOut = dir.resolve("e.csv")

        val source = TinkerGraphOperations()
        val alice = source.createVertex("Person", mapOf("name" to "Alice"))
        val bob = source.createVertex("Person", mapOf("name" to "Bob"))
        source.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2024"))

        val exporter = CsvGraphVirtualThreadBulkExporter()
        exporter.exportGraphAsync(
            CsvGraphExportSink(GraphExportSink.PathSink(vOut), GraphExportSink.PathSink(eOut)),
            source,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).get().status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = CsvGraphVirtualThreadBulkImporter()
        val report = importer.importGraphAsync(
            CsvGraphImportSource(GraphImportSource.PathSource(vOut), GraphImportSource.PathSource(eOut)),
            target,
            GraphImportOptions(),
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

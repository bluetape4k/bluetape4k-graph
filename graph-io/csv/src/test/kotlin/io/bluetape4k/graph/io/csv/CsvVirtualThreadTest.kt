package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
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
        val alice = source.createVertex("Person", "name" to "Alice")
        val bob = source.createVertex("Person", "name" to "Bob")
        source.createEdge(alice.id, bob.id, "KNOWS", "since" to "2024")

        val exporter = CsvGraphVirtualThreadBulkExporter()
        exporter
            .exportGraphAsync(
                CsvGraphExportSink(vOut.asSink(), eOut.asSink()),
                source,
                GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
            )
            .get().status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = CsvGraphVirtualThreadBulkImporter()
        val report = importer
            .importGraphAsync(
                CsvGraphImportSource(vOut.asSource(), eOut.asSource()),
                target,
                GraphImportOptions(),
            )
            .get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

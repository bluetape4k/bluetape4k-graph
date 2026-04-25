package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GraphMlVirtualThreadTest {

    @Test
    fun `virtual thread import and export round trip`(@TempDir dir: Path) {
        val out = dir.resolve("graph-vt.graphml")

        val src = TinkerGraphOperations()
        val a = src.createVertex("City", mapOf("name" to "Seoul"))
        val b = src.createVertex("City", mapOf("name" to "Busan"))
        src.createEdge(a.id, b.id, "ROAD", mapOf("km" to 400))

        val exportReport = GraphMlVirtualThreadBulkExporter().exportGraphAsync(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("City"), edgeLabels = setOf("ROAD")),
        ).get()
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importReport = GraphMlVirtualThreadBulkImporter().importGraphAsync(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        ).get()
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }
}

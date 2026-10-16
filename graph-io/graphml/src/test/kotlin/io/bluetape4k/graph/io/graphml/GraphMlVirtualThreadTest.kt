package io.bluetape4k.graph.io.graphml

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GraphMlVirtualThreadTest {

    companion object: KLogging()

    @Test
    fun `virtual thread import and export round trip`(@TempDir dir: Path) {
        val out = dir.resolve("graph-vt.graphml")

        val src = TinkerGraphOperations()
        val a = src.createVertex("City", "name" to "Seoul")
        val b = src.createVertex("City", "name" to "Busan")
        src.createEdge(a.id, b.id, "ROAD", "km" to 400)

        val exportReport = GraphMlVirtualThreadBulkExporter()
            .exportGraphAsync(
                out.asSink(),
                src,
                GraphExportOptions(vertexLabels = setOf("City"), edgeLabels = setOf("ROAD")),
            ).get()

        log.debug { "exportReport=$exportReport" }
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importReport = GraphMlVirtualThreadBulkImporter()
            .importGraphAsync(
                out.asSource(),
                target,
                GraphImportOptions(),
            ).get()

        log.debug { "importReport=$importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }
}

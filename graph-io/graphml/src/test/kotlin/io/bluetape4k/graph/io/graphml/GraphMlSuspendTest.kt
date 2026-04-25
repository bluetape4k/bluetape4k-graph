package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GraphMlSuspendTest {

    @Test
    fun `suspend import and export round trip`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("graph-suspend.graphml")

        val src = TinkerGraphOperations()
        val a = src.createVertex("Product", mapOf("name" to "Widget", "price" to 9.99))
        val b = src.createVertex("Product", mapOf("name" to "Gadget", "price" to 19.99))
        src.createEdge(a.id, b.id, "SIMILAR", mapOf("score" to 0.8))

        val suspendSrc = TinkerGraphSuspendOperations(src)
        val suspendTarget = TinkerGraphSuspendOperations(TinkerGraphOperations())

        val exportReport = SuspendGraphMlBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(out),
            suspendSrc,
            GraphExportOptions(vertexLabels = setOf("Product"), edgeLabels = setOf("SIMILAR")),
        )
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        val importReport = SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(out),
            suspendTarget,
            GraphImportOptions(),
        )
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }
}

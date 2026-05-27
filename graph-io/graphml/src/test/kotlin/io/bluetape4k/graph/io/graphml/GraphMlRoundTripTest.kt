package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class GraphMlRoundTripTest {

    @Test
    fun `sync round trip three vertices two edges`(@TempDir dir: Path) {
        val out = dir.resolve("graph.graphml")

        val src = TinkerGraphOperations()
        val alice = src.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
        val bob = src.createVertex("Person", mapOf("name" to "Bob", "age" to 25))
        val charlie = src.createVertex("Person", mapOf("name" to "Charlie", "age" to 22))
        src.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2020"))
        src.createEdge(bob.id, charlie.id, "KNOWS", mapOf("since" to "2022"))

        val exporter = GraphMlBulkExporter()
        exporter.exportGraph(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val target = TinkerGraphOperations()
        val importer = GraphMlBulkImporter()
        val report = importer.importGraph(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesCreated shouldBeEqualTo 2L
    }

    @Test
    fun `sync round trip with integer and double properties`(@TempDir dir: Path) {
        val out = dir.resolve("typed.graphml")

        val src = TinkerGraphOperations()
        val n1 = src.createVertex("Item", mapOf("price" to 9.99, "stock" to 100))
        val n2 = src.createVertex("Item", mapOf("price" to 4.5, "stock" to 50))
        src.createEdge(n1.id, n2.id, "RELATED", emptyMap())

        GraphMlBulkExporter().exportGraph(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("Item"), edgeLabels = setOf("RELATED")),
        )

        val target = TinkerGraphOperations()
        val report = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(out),
            target,
            GraphImportOptions(),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `duplicate vertex skip policy records partial report failure`(@TempDir dir: Path) {
        val graphml = dir.resolve("duplicate.graphml").also {
            it.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/graphml">
  <graph id="G" edgedefault="directed">
    <node id="n1"/>
    <node id="n1"/>
  </graph>
</graphml>""",
            )
        }

        val report = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(graphml),
            TinkerGraphOperations(),
            GraphImportOptions(onDuplicateVertexId = DuplicateVertexPolicy.SKIP),
        )

        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.verticesRead shouldBeEqualTo 2L
        report.verticesCreated shouldBeEqualTo 1L
        report.skippedVertices shouldBeEqualTo 1L
        report.failures shouldHaveSize 1
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
        report.failures.single().message shouldContain "Duplicate vertex skipped"
    }

    @Test
    fun `missing endpoint skip policy records partial report failure`(@TempDir dir: Path) {
        val graphml = dir.resolve("missing-endpoint.graphml").also {
            it.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/graphml">
  <graph id="G" edgedefault="directed">
    <node id="n1"/>
    <edge id="e1" source="n1" target="missing"/>
  </graph>
</graphml>""",
            )
        }

        val report = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(graphml),
            TinkerGraphOperations(),
            GraphImportOptions(onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE),
        )

        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.verticesCreated shouldBeEqualTo 1L
        report.edgesRead shouldBeEqualTo 1L
        report.edgesCreated shouldBeEqualTo 0L
        report.skippedEdges shouldBeEqualTo 1L
        report.failures shouldHaveSize 1
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
        report.failures.single().message shouldContain "Missing endpoint skipped"
    }

    @Test
    fun `unsupported port fail policy returns failed report without creating elements`(@TempDir dir: Path) {
        val graphml = dir.resolve("unsupported-port.graphml").also {
            it.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/graphml">
  <graph id="G" edgedefault="directed">
    <node id="n1">
      <port name="p1"/>
    </node>
  </graph>
</graphml>""",
            )
        }

        val report = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(graphml),
            TinkerGraphOperations(),
            GraphImportOptions(),
            GraphMlImportOptions(unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.verticesRead shouldBeEqualTo 1L
        report.verticesCreated shouldBeEqualTo 0L
        report.failures shouldHaveSize 1
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.ERROR
        report.failures.single().elementName shouldBeEqualTo "port"
    }
}

package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.graphml.UnsupportedGraphMlElementPolicy
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldHaveSize
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class StaxGraphMlReaderWriterTest {

    private val writer = StaxGraphMlWriter()
    private val reader = StaxGraphMlReader()

    @Test
    fun `write and read empty graph`() {
        val out = ByteArrayOutputStream()
        writer.write(out, emptyList(), emptyList())
        val result = reader.read(ByteArrayInputStream(out.toByteArray()))
        result.vertices shouldHaveSize 0
        result.edges shouldHaveSize 0
        result.failures shouldHaveSize 0
    }

    @Test
    fun `write and read vertices only`() {
        val vertices = listOf(
            GraphIoVertexRecord("n1", "Person", mapOf("name" to "Alice", "age" to 30)),
            GraphIoVertexRecord("n2", "Person", mapOf("name" to "Bob", "age" to 25)),
        )
        val out = ByteArrayOutputStream()
        writer.write(out, vertices, emptyList())

        val result = reader.read(ByteArrayInputStream(out.toByteArray()))
        result.failures shouldHaveSize 0
        result.vertices shouldHaveSize 2
        result.vertices[0].externalId shouldBeEqualTo "n1"
        result.vertices[0].label shouldBeEqualTo "Person"
        result.vertices[0].properties["name"] shouldBeEqualTo "Alice"
        result.vertices[1].externalId shouldBeEqualTo "n2"
        result.vertices[1].properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    fun `write and read vertices and edges`() {
        val vertices = listOf(
            GraphIoVertexRecord("n1", "Person", mapOf("name" to "Alice")),
            GraphIoVertexRecord("n2", "Person", mapOf("name" to "Bob")),
        )
        val edges = listOf(
            GraphIoEdgeRecord("e1", "KNOWS", "n1", "n2", mapOf("since" to "2020")),
        )
        val out = ByteArrayOutputStream()
        writer.write(out, vertices, edges)

        val result = reader.read(ByteArrayInputStream(out.toByteArray()))
        result.failures shouldHaveSize 0
        result.vertices shouldHaveSize 2
        result.edges shouldHaveSize 1
        result.edges[0].externalId shouldBeEqualTo "e1"
        result.edges[0].label shouldBeEqualTo "KNOWS"
        result.edges[0].fromExternalId shouldBeEqualTo "n1"
        result.edges[0].toExternalId shouldBeEqualTo "n2"
        result.edges[0].properties["since"] shouldBeEqualTo "2020"
    }

    @Test
    fun `edge without id is parsed correctly`() {
        val vertices = listOf(
            GraphIoVertexRecord("n1", "City", mapOf("name" to "Seoul")),
            GraphIoVertexRecord("n2", "City", mapOf("name" to "Busan")),
        )
        val edges = listOf(
            GraphIoEdgeRecord(null, "ROAD", "n1", "n2", emptyMap()),
        )
        val out = ByteArrayOutputStream()
        writer.write(out, vertices, edges)

        val result = reader.read(ByteArrayInputStream(out.toByteArray()))
        result.failures shouldHaveSize 0
        result.edges shouldHaveSize 1
        result.edges[0].fromExternalId shouldBeEqualTo "n1"
        result.edges[0].externalId shouldBeEqualTo null
    }

    @Test
    fun `reader handles missing label using default`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/graphml">
  <graph id="G" edgedefault="directed">
    <node id="n1"/>
  </graph>
</graphml>"""
        val options = GraphMlImportOptions(defaultVertexLabel = "DefaultVertex")
        val result = reader.read(ByteArrayInputStream(xml.toByteArray()), options)
        result.failures shouldHaveSize 0
        result.vertices shouldHaveSize 1
        result.vertices[0].label shouldBeEqualTo "DefaultVertex"
    }

    @Test
    fun `reader imports representative property graph fixture`() {
        val result = fixture("property-graph-basic.graphml").use { reader.read(it) }

        result.failures shouldHaveSize 0
        result.vertices shouldHaveSize 2
        result.edges shouldHaveSize 1
        result.vertices[0].externalId shouldBeEqualTo "n1"
        result.vertices[0].label shouldBeEqualTo "Person"
        result.vertices[0].properties["name"] shouldBeEqualTo "Alice"
        result.vertices[0].properties["age"] shouldBeEqualTo 31
        result.edges[0].externalId shouldBeEqualTo "e1"
        result.edges[0].label shouldBeEqualTo "KNOWS"
        result.edges[0].properties["score"] shouldBeEqualTo 0.75
    }

    @Test
    fun `reader records failure for edge missing source`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/graphml">
  <graph id="G" edgedefault="directed">
    <node id="n1"/>
    <edge id="e1" target="n1"/>
  </graph>
</graphml>"""
        val result = reader.read(ByteArrayInputStream(xml.toByteArray()))
        result.failures.isEmpty().not().shouldBeTrue()
    }

    @Test
    fun `reader records warnings for unsupported graphml fixture with SKIP policy`() {
        val result = fixture("unsupported-constructs.graphml").use { reader.read(it) }

        result.vertices shouldHaveSize 1
        result.edges shouldHaveSize 1
        result.failures shouldHaveSize 5
        result.failures.map { it.severity }.toSet() shouldBeEqualTo setOf(GraphIoFailureSeverity.WARN)
        result.failures.map { it.elementName } shouldContain "graph"
        result.failures.map { it.elementName } shouldContain "port"
        result.failures.map { it.elementName } shouldContain "hyperedge"
        result.failures.map { it.message } shouldContain "Nested GraphML graphs are not supported"
        result.failures.map { it.message } shouldContain "GraphML undirected edges are not supported"
    }

    @Test
    fun `reader records errors for unsupported graphml fixture with FAIL policy`() {
        val result = fixture("unsupported-constructs.graphml").use {
            reader.read(
                it,
                GraphMlImportOptions(unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL),
            )
        }

        result.vertices shouldHaveSize 1
        result.edges shouldHaveSize 1
        result.failures shouldHaveSize 5
        result.failures.map { it.severity }.toSet() shouldBeEqualTo setOf(GraphIoFailureSeverity.ERROR)
        result.failures.map { it.message } shouldContain "GraphML undirected graphs are not supported"
        result.failures.map { it.message } shouldContain "Nested GraphML graphs are not supported"
        result.failures.map { it.message } shouldContain "GraphML undirected edges are not supported"
    }

    @Test
    fun `bulk importer fails before writes for strict unsupported graphml fixture`() {
        val report = GraphMlBulkImporter().importGraph(
            GraphImportSource.InputStreamSource(fixture("unsupported-constructs.graphml"), closeInput = true),
            TinkerGraphOperations(),
            GraphImportOptions(),
            GraphMlImportOptions(unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.verticesRead shouldBeEqualTo 1L
        report.verticesCreated shouldBeEqualTo 0L
        report.edgesRead shouldBeEqualTo 1L
        report.edgesCreated shouldBeEqualTo 0L
        report.failures shouldHaveSize 5
        report.failures.singleOrNull { it.message.contains("hyperedge") }?.severity shouldBeEqualTo
            GraphIoFailureSeverity.ERROR
    }

    @Test
    fun `reader records one error for undirected graph default`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/graphml">
  <graph id="G" edgedefault="undirected">
    <node id="n1"/>
  </graph>
</graphml>"""
        val result = reader.read(
            ByteArrayInputStream(xml.toByteArray()),
            GraphMlImportOptions(unsupportedElementPolicy = UnsupportedGraphMlElementPolicy.FAIL),
        )

        result.vertices shouldHaveSize 1
        result.failures shouldHaveSize 1
        result.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.ERROR
        result.failures.single().message shouldContain "undirected graphs"
    }

    @Test
    fun `write and read custom export options`() {
        val vertices = listOf(GraphIoVertexRecord("v1", "Item", mapOf("price" to 9.99)))
        val exportOpts = GraphMlExportOptions(graphId = "MyGraph", labelAttrName = "type")
        val importOpts = GraphMlImportOptions(labelAttrName = "type")

        val out = ByteArrayOutputStream()
        writer.write(out, vertices, emptyList(), exportOpts)

        val result = reader.read(ByteArrayInputStream(out.toByteArray()), importOpts)
        result.vertices shouldHaveSize 1
        result.vertices[0].label shouldBeEqualTo "Item"
    }

    private fun fixture(name: String): InputStream =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/graphml/$name")) {
            "GraphML fixture not found: $name"
        }
}

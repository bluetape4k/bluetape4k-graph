package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
}

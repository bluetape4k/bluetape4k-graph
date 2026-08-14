package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.io.OutputStream
import javax.xml.stream.XMLStreamWriter
import javax.xml.stream.XMLOutputFactory

/**
 * StAX 기반 GraphML 라이터.
 * `<key>` 정의를 먼저 쓴 뒤 `<graph>` 내부에 `<node>` / `<edge>` 요소를 순차 기록한다.
 *
 * GraphML header에 전역 property key 정의가 필요하므로 exporter는 key 이름만
 * bounded pre-scan으로 수집하고, 실제 record는 [GraphMlWriteSession]에 한 번에
 * 하나의 chunk씩 전달한다.
 */
internal class StaxGraphMlWriter {

    fun write(
        output: OutputStream,
        vertices: List<GraphIoVertexRecord>,
        edges: List<GraphIoEdgeRecord>,
        options: GraphMlExportOptions = GraphMlExportOptions(),
    ): GraphMlWriteResult {
        val vertexPropertyKeys = vertices.asSequence()
            .flatMap { it.properties.keys.asSequence() }
            .toSet()
        val edgePropertyKeys = edges.asSequence()
            .flatMap { it.properties.keys.asSequence() }
            .toSet()

        return write(
            output = output,
            vertices = vertices.asSequence(),
            edges = edges.asSequence(),
            options = options,
            vertexPropertyKeys = vertexPropertyKeys,
            edgePropertyKeys = edgePropertyKeys,
        )
    }

    fun write(
        output: OutputStream,
        vertices: Sequence<GraphIoVertexRecord>,
        edges: Sequence<GraphIoEdgeRecord>,
        options: GraphMlExportOptions,
        vertexPropertyKeys: Set<String>,
        edgePropertyKeys: Set<String>,
    ): GraphMlWriteResult {
        output.use { outputStream ->
            open(outputStream, options, vertexPropertyKeys, edgePropertyKeys).use { session ->
                vertices.forEach(session::writeVertex)
                edges.forEach(session::writeEdge)
                session.finish()
                return session.result()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun open(
        output: OutputStream,
        options: GraphMlExportOptions,
        vertexPropertyKeys: Set<String>,
        edgePropertyKeys: Set<String>,
    ): GraphMlWriteSession {
        val xmlWriter = factory.createXMLStreamWriter(output, options.encoding)
        return try {
            GraphMlWriteSession(xmlWriter, options, vertexPropertyKeys, edgePropertyKeys)
        } catch (e: Throwable) {
            xmlWriter.close()
            throw e
        }
    }

    companion object : KLogging() {
        private val factory: XMLOutputFactory = XMLOutputFactory.newInstance()
    }
}

internal data class GraphMlWriteResult(
    val verticesWritten: Long,
    val edgesWritten: Long,
)

internal class GraphMlWriteSession internal constructor(
    private val writer: XMLStreamWriter,
    private val options: GraphMlExportOptions,
    vertexPropertyKeys: Set<String>,
    edgePropertyKeys: Set<String>,
) : AutoCloseable {

    private val keyMap = mutableMapOf<String, String>()
    private var keyIndex = 0
    private var finished = false
    private var closed = false
    private var verticesWritten = 0L
    private var edgesWritten = 0L

    init {
        writeHeader(vertexPropertyKeys, edgePropertyKeys)
    }

    fun writeVertex(vertex: GraphIoVertexRecord) {
        checkOpen()
        writer.writeCharacters("    ")
        writer.writeStartElement("node")
        writer.writeAttribute("id", vertex.externalId)
        writer.writeCharacters("\n")

        keyMap["node:${options.labelAttrName}"]?.let { keyId ->
            writeDataElement(keyId, vertex.label)
        }
        for ((attrName, value) in vertex.properties) {
            keyMap["node:$attrName"]?.let { keyId ->
                writeDataElement(keyId, value?.toString() ?: "")
            }
        }

        writer.writeCharacters("    ")
        writer.writeEndElement()
        writer.writeCharacters("\n")
        verticesWritten++
    }

    fun writeVertices(vertices: Iterable<GraphIoVertexRecord>) {
        vertices.forEach(::writeVertex)
    }

    fun writeEdge(edge: GraphIoEdgeRecord) {
        checkOpen()
        writer.writeCharacters("    ")
        writer.writeStartElement("edge")
        edge.externalId?.let { writer.writeAttribute("id", it) }
        writer.writeAttribute("source", edge.fromExternalId)
        writer.writeAttribute("target", edge.toExternalId)
        writer.writeCharacters("\n")

        keyMap["edge:${options.labelAttrName}"]?.let { keyId ->
            writeDataElement(keyId, edge.label)
        }
        for ((attrName, value) in edge.properties) {
            keyMap["edge:$attrName"]?.let { keyId ->
                writeDataElement(keyId, value?.toString() ?: "")
            }
        }

        writer.writeCharacters("    ")
        writer.writeEndElement()
        writer.writeCharacters("\n")
        edgesWritten++
    }

    fun writeEdges(edges: Iterable<GraphIoEdgeRecord>) {
        edges.forEach(::writeEdge)
    }

    fun finish() {
        checkOpen()
        if (finished) return

        writer.writeCharacters("  ")
        writer.writeEndElement() // graph
        writer.writeCharacters("\n")
        writer.writeEndElement() // graphml
        writer.writeEndDocument()
        writer.flush()
        finished = true
        log.debug { "GraphML write completed: vertices=$verticesWritten, edges=$edgesWritten" }
    }

    fun result(): GraphMlWriteResult = GraphMlWriteResult(verticesWritten, edgesWritten)

    override fun close() {
        if (closed) return
        closed = true
        writer.close()
    }

    private fun writeHeader(vertexPropertyKeys: Set<String>, edgePropertyKeys: Set<String>) {
        writer.writeStartDocument(options.encoding, "1.0")
        writer.writeCharacters("\n")
        writer.writeStartElement("graphml")
        writer.writeAttribute("xmlns", "http://graphml.graphdrawing.org/graphml")
        writer.writeCharacters("\n")

        fun writeKeyDef(forElement: String, attrName: String) {
            val keyId = "d${keyIndex++}"
            keyMap["$forElement:$attrName"] = keyId
            writer.writeCharacters("  ")
            writer.writeStartElement("key")
            writer.writeAttribute("id", keyId)
            writer.writeAttribute("for", forElement)
            writer.writeAttribute("attr.name", attrName)
            writer.writeAttribute("attr.type", "string")
            writer.writeEndElement()
            writer.writeCharacters("\n")
        }

        writeKeyDef("node", options.labelAttrName)
        vertexPropertyKeys.sorted().forEach { writeKeyDef("node", it) }
        writeKeyDef("edge", options.labelAttrName)
        edgePropertyKeys.sorted().forEach { writeKeyDef("edge", it) }

        writer.writeCharacters("  ")
        writer.writeStartElement("graph")
        writer.writeAttribute("id", options.graphId)
        writer.writeAttribute("edgedefault", options.edgeDefault.xmlName)
        writer.writeCharacters("\n")
    }

    private fun writeDataElement(keyId: String, value: String) {
        writer.writeCharacters("      ")
        writer.writeStartElement("data")
        writer.writeAttribute("key", keyId)
        writer.writeCharacters(value)
        writer.writeEndElement()
        writer.writeCharacters("\n")
    }

    private fun checkOpen() {
        check(!closed) { "GraphML writer session is closed" }
        check(!finished) { "GraphML writer session is finished" }
    }

    companion object : KLogging()
}

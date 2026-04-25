package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.io.OutputStream
import javax.xml.stream.XMLOutputFactory

/**
 * StAX 기반 GraphML 라이터.
 * `<key>` 정의를 먼저 쓴 뒤 `<graph>` 내부에 `<node>` / `<edge>` 요소를 순차 기록한다.
 */
internal class StaxGraphMlWriter {

    fun write(
        output: OutputStream,
        vertices: List<GraphIoVertexRecord>,
        edges: List<GraphIoEdgeRecord>,
        options: GraphMlExportOptions = GraphMlExportOptions(),
    ) {
        log.debug { "Writing GraphML: vertices=${vertices.size}, edges=${edges.size}" }

        val vertexPropKeys = vertices.flatMap { it.properties.keys }.distinct().sorted()
        val edgePropKeys = edges.flatMap { it.properties.keys }.distinct().sorted()

        val writer = factory.createXMLStreamWriter(output, options.encoding)

        try {
            writer.writeStartDocument(options.encoding, "1.0")
            writer.writeCharacters("\n")
            writer.writeStartElement("graphml")
            writer.writeAttribute("xmlns", "http://graphml.graphdrawing.org/graphml")
            writer.writeCharacters("\n")

            val keyMap = mutableMapOf<String, String>()
            var keyIdx = 0

            fun writeKeyDef(forEl: String, attrName: String) {
                val keyId = "d${keyIdx++}"
                keyMap["$forEl:$attrName"] = keyId
                writer.writeCharacters("  ")
                writer.writeStartElement("key")
                writer.writeAttribute("id", keyId)
                writer.writeAttribute("for", forEl)
                writer.writeAttribute("attr.name", attrName)
                writer.writeAttribute("attr.type", "string")
                writer.writeEndElement()
                writer.writeCharacters("\n")
            }

            writeKeyDef("node", options.labelAttrName)
            vertexPropKeys.forEach { writeKeyDef("node", it) }
            writeKeyDef("edge", options.labelAttrName)
            edgePropKeys.forEach { writeKeyDef("edge", it) }

            writer.writeCharacters("  ")
            writer.writeStartElement("graph")
            writer.writeAttribute("id", options.graphId)
            writer.writeAttribute("edgedefault", options.edgeDefault.xmlName)
            writer.writeCharacters("\n")

            for (v in vertices) {
                writer.writeCharacters("    ")
                writer.writeStartElement("node")
                writer.writeAttribute("id", v.externalId)
                writer.writeCharacters("\n")

                keyMap["node:${options.labelAttrName}"]?.let { keyId ->
                    writeDataElement(writer, keyId, v.label)
                }
                for ((attrName, value) in v.properties) {
                    keyMap["node:$attrName"]?.let { keyId ->
                        writeDataElement(writer, keyId, value?.toString() ?: "")
                    }
                }

                writer.writeCharacters("    ")
                writer.writeEndElement()
                writer.writeCharacters("\n")
            }

            for (e in edges) {
                writer.writeCharacters("    ")
                writer.writeStartElement("edge")
                e.externalId?.let { writer.writeAttribute("id", it) }
                writer.writeAttribute("source", e.fromExternalId)
                writer.writeAttribute("target", e.toExternalId)
                writer.writeCharacters("\n")

                keyMap["edge:${options.labelAttrName}"]?.let { keyId ->
                    writeDataElement(writer, keyId, e.label)
                }
                for ((attrName, value) in e.properties) {
                    keyMap["edge:$attrName"]?.let { keyId ->
                        writeDataElement(writer, keyId, value?.toString() ?: "")
                    }
                }

                writer.writeCharacters("    ")
                writer.writeEndElement()
                writer.writeCharacters("\n")
            }

            writer.writeCharacters("  ")
            writer.writeEndElement() // graph
            writer.writeCharacters("\n")
            writer.writeEndElement() // graphml
            writer.writeEndDocument()
            writer.flush()
        } finally {
            writer.close()
        }

        log.debug { "GraphML write completed: vertices=${vertices.size}, edges=${edges.size}" }
    }

    private fun writeDataElement(writer: javax.xml.stream.XMLStreamWriter, keyId: String, value: String) {
        writer.writeCharacters("      ")
        writer.writeStartElement("data")
        writer.writeAttribute("key", keyId)
        writer.writeCharacters(value)
        writer.writeEndElement()
        writer.writeCharacters("\n")
    }

    companion object : KLogging() {
        private val factory: XMLOutputFactory = XMLOutputFactory.newInstance()
    }
}

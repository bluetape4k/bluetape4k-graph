package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlAttrType
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * StAX 기반 GraphML 리더.
 * `<key>` 정의를 파싱하여 ID→속성명/타입 맵을 구축하고, `<node>`/`<edge>` 요소를 순차 파싱한다.
 */
internal class StaxGraphMlReader {

    data class GraphMlReadResult(
        val vertices: List<GraphIoVertexRecord>,
        val edges: List<GraphIoEdgeRecord>,
        val failures: List<GraphIoFailure>,
    )

    fun read(input: InputStream, options: GraphMlImportOptions = GraphMlImportOptions()): GraphMlReadResult {
        log.debug { "Parsing GraphML stream: labelAttrName=${options.labelAttrName}" }

        val reader = factory.createXMLStreamReader(input)

        val keyIdToName = mutableMapOf<String, String>()
        val keyIdToType = mutableMapOf<String, GraphMlAttrType>()
        val vertices = mutableListOf<GraphIoVertexRecord>()
        val edges = mutableListOf<GraphIoEdgeRecord>()
        val failures = mutableListOf<GraphIoFailure>()

        try {
            while (reader.hasNext()) {
                val event = reader.next()
                if (event == XMLStreamConstants.START_ELEMENT) {
                    when (reader.localName) {
                        "key" -> parseKey(reader, keyIdToName, keyIdToType)
                        "node" -> parseNode(reader, keyIdToName, keyIdToType, options, vertices, failures)
                        "edge" -> parseEdge(reader, keyIdToName, keyIdToType, options, edges, failures)
                    }
                }
            }
        } finally {
            reader.close()
        }

        log.debug { "GraphML parsed: vertices=${vertices.size}, edges=${edges.size}, failures=${failures.size}" }
        return GraphMlReadResult(vertices, edges, failures)
    }

    private fun parseKey(
        reader: XMLStreamReader,
        keyIdToName: MutableMap<String, String>,
        keyIdToType: MutableMap<String, GraphMlAttrType>,
    ) {
        val id = reader.getAttributeValue(null, "id") ?: return
        val name = reader.getAttributeValue(null, "attr.name") ?: return
        val typeStr = reader.getAttributeValue(null, "attr.type") ?: "string"
        keyIdToName[id] = name
        keyIdToType[id] = GraphMlAttrType.fromXml(typeStr)
    }

    private fun parseNode(
        reader: XMLStreamReader,
        keyIdToName: Map<String, String>,
        keyIdToType: Map<String, GraphMlAttrType>,
        options: GraphMlImportOptions,
        vertices: MutableList<GraphIoVertexRecord>,
        failures: MutableList<GraphIoFailure>,
    ) {
        val nodeId = reader.getAttributeValue(null, "id")
        if (nodeId.isNullOrBlank()) {
            failures += GraphIoFailure(
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.UNIFIED,
                message = "Node missing id attribute",
            )
            return
        }

        val dataMap = readDataChildren(reader, "node")

        var label = options.defaultVertexLabel
        val props = mutableMapOf<String, Any?>()
        for ((keyId, rawValue) in dataMap) {
            val attrName = keyIdToName[keyId] ?: keyId
            val attrType = keyIdToType[keyId] ?: GraphMlAttrType.STRING
            if (attrName == options.labelAttrName) {
                label = rawValue
            } else {
                props[attrName] = attrType.coerce(rawValue)
            }
        }
        vertices += GraphIoVertexRecord(externalId = nodeId, label = label, properties = props)
    }

    private fun parseEdge(
        reader: XMLStreamReader,
        keyIdToName: Map<String, String>,
        keyIdToType: Map<String, GraphMlAttrType>,
        options: GraphMlImportOptions,
        edges: MutableList<GraphIoEdgeRecord>,
        failures: MutableList<GraphIoFailure>,
    ) {
        val edgeId = reader.getAttributeValue(null, "id")
        val source = reader.getAttributeValue(null, "source")
        val target = reader.getAttributeValue(null, "target")
        if (source.isNullOrBlank() || target.isNullOrBlank()) {
            failures += GraphIoFailure(
                phase = GraphIoPhase.READ_EDGE,
                fileRole = GraphIoFileRole.UNIFIED,
                recordId = edgeId,
                message = "Edge missing source/target: source=$source target=$target",
            )
            return
        }

        val dataMap = readDataChildren(reader, "edge")

        var label = options.defaultEdgeLabel
        val props = mutableMapOf<String, Any?>()
        for ((keyId, rawValue) in dataMap) {
            val attrName = keyIdToName[keyId] ?: keyId
            val attrType = keyIdToType[keyId] ?: GraphMlAttrType.STRING
            if (attrName == options.labelAttrName) {
                label = rawValue
            } else {
                props[attrName] = attrType.coerce(rawValue)
            }
        }
        edges += GraphIoEdgeRecord(
            externalId = edgeId,
            label = label,
            fromExternalId = source,
            toExternalId = target,
            properties = props,
        )
    }

    /** 현재 요소의 `<data key="...">text</data>` 자식들을 읽어 keyId→value 맵으로 반환한다. */
    private fun readDataChildren(reader: XMLStreamReader, parentLocalName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.localName == "data") {
                        val key = reader.getAttributeValue(null, "key") ?: continue
                        val value = reader.elementText ?: ""
                        result[key] = value
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == parentLocalName) break
                }
            }
        }
        return result
    }

    companion object : KLogging() {
        private val factory: XMLInputFactory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
    }
}

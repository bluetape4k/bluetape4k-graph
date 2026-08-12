package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlAttrType
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.graphml.UnsupportedGraphMlElementPolicy
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.FilterInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * StAX 기반 GraphML 리더.
 * `<key>` 정의를 파싱하여 ID→속성명/타입 맵을 구축하고, `<node>`/`<edge>` 요소를 순차 파싱한다.
 */
@Suppress("TooManyFunctions")
internal class StaxGraphMlReader {

    internal sealed interface GraphMlRecordEvent {
        data class Vertex(val record: GraphIoVertexRecord) : GraphMlRecordEvent
        data class Edge(val record: GraphIoEdgeRecord) : GraphMlRecordEvent
        data class Failure(val failure: GraphIoFailure) : GraphMlRecordEvent
    }

    internal interface GraphMlRecordSink {
        fun onVertex(record: GraphIoVertexRecord)
        fun onEdge(record: GraphIoEdgeRecord)
        fun onFailure(failure: GraphIoFailure)
    }

    data class GraphMlReadResult(
        val vertices: List<GraphIoVertexRecord>,
        val edges: List<GraphIoEdgeRecord>,
        val failures: List<GraphIoFailure>,
    )

    fun read(input: InputStream, options: GraphMlImportOptions = GraphMlImportOptions()): GraphMlReadResult {
        val vertices = mutableListOf<GraphIoVertexRecord>()
        val edges = mutableListOf<GraphIoEdgeRecord>()
        val failures = mutableListOf<GraphIoFailure>()
        read(
            input = input,
            options = options,
            sink = object : GraphMlRecordSink {
                override fun onVertex(record: GraphIoVertexRecord) {
                    vertices += record
                }

                override fun onEdge(record: GraphIoEdgeRecord) {
                    edges += record
                }

                override fun onFailure(failure: GraphIoFailure) {
                    failures += failure
                }
            },
        )
        return GraphMlReadResult(vertices, edges, failures)
    }

    fun read(
        input: InputStream,
        options: GraphMlImportOptions = GraphMlImportOptions(),
        sink: GraphMlRecordSink,
    ) {
        log.debug { "Parsing GraphML stream: labelAttrName=${options.labelAttrName}" }

        val trackedInput = NonClosingInputStream(input)
        val reader = factory.createXMLStreamReader(trackedInput)

        val keyIdToName = mutableMapOf<String, String>()
        val keyIdToType = mutableMapOf<String, GraphMlAttrType>()
        var currentPhase = GraphIoPhase.READ_VERTEX

        try {
            while (reader.hasNext()) {
                val event = reader.next()
                if (event == XMLStreamConstants.START_ELEMENT) {
                    when (reader.localName) {
                        "key" -> parseKey(reader, keyIdToName, keyIdToType)
                        "graph" -> recordUnsupportedGraph(reader, options, sink)
                        "hyperedge", "port" -> recordUnsupportedElement(reader, options, sink)
                        "node" -> {
                            currentPhase = GraphIoPhase.READ_VERTEX
                            parseNode(reader, keyIdToName, keyIdToType, options, sink)
                        }
                        "edge" -> {
                            currentPhase = GraphIoPhase.READ_EDGE
                            parseEdge(reader, keyIdToName, keyIdToType, options, sink)
                        }
                    }
                }
            }
        } catch (_: XMLStreamException) {
            sink.onFailure(
                GraphIoFailure(
                    phase = trackedInput.malformedRecordPhase ?: currentPhase,
                    fileRole = GraphIoFileRole.UNIFIED,
                    location = reader.location.lineNumber.takeIf { it > 0 }?.let { "line:$it" },
                    message = "Malformed GraphML",
                ),
            )
        } finally {
            reader.close()
        }

        log.debug { "GraphML parsed with streaming sink" }
    }

    fun events(
        input: InputStream,
        options: GraphMlImportOptions = GraphMlImportOptions(),
    ): Flow<GraphMlRecordEvent> = channelFlow {
        val producer = this
        withContext(Dispatchers.IO) {
            read(
                input = input,
                options = options,
                sink = object : GraphMlRecordSink {
                    override fun onVertex(record: GraphIoVertexRecord) {
                        producer.sendEvent(GraphMlRecordEvent.Vertex(record))
                    }

                    override fun onEdge(record: GraphIoEdgeRecord) {
                        producer.sendEvent(GraphMlRecordEvent.Edge(record))
                    }

                    override fun onFailure(failure: GraphIoFailure) {
                        producer.sendEvent(GraphMlRecordEvent.Failure(failure))
                    }
                },
            )
        }
    }

    private fun ProducerScope<GraphMlRecordEvent>.sendEvent(event: GraphMlRecordEvent) {
        val result = trySendBlocking(event)
        if (result.isSuccess) return
        if (coroutineContext.isActive) {
            throw result.exceptionOrNull()
                ?: IllegalStateException("GraphML event channel closed")
        }
        throw CancellationException("GraphML collection cancelled")
    }

    private fun recordUnsupportedGraph(
        reader: XMLStreamReader,
        options: GraphMlImportOptions,
        sink: GraphMlRecordSink,
    ) {
        if (reader.getAttributeValue(null, "edgedefault") == "undirected") {
            recordUnsupportedElement(reader, options, sink, "GraphML undirected graphs are not supported")
        }
    }

    private fun recordUnsupportedElement(
        reader: XMLStreamReader,
        options: GraphMlImportOptions,
        sink: GraphMlRecordSink,
        message: String = "Unsupported GraphML element: ${reader.localName}",
        phase: GraphIoPhase = GraphIoPhase.READ_VERTEX,
    ) {
        sink.onFailure(
            GraphIoFailure(
                phase = phase,
                severity = when (options.unsupportedElementPolicy) {
                    UnsupportedGraphMlElementPolicy.SKIP -> GraphIoFailureSeverity.WARN
                    UnsupportedGraphMlElementPolicy.FAIL -> GraphIoFailureSeverity.ERROR
                },
                fileRole = GraphIoFileRole.UNIFIED,
                elementName = reader.localName,
                message = message,
            ),
        )
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
        sink: GraphMlRecordSink,
    ) {
        val nodeId = reader.getAttributeValue(null, "id")
        if (nodeId.isNullOrBlank()) {
            sink.onFailure(
                GraphIoFailure(
                    phase = GraphIoPhase.READ_VERTEX,
                    fileRole = GraphIoFileRole.UNIFIED,
                    message = "Node missing id attribute",
                ),
            )
            return
        }

        val dataMap = readDataChildren(reader, "node", options, sink)

        var label = options.defaultVertexLabel
        val props = mutableMapOf<String, Any?>()
        for ((keyId, data) in dataMap) {
            val attrName = keyIdToName[keyId] ?: keyId
            val attrType = keyIdToType[keyId] ?: GraphMlAttrType.STRING
            if (attrName == options.labelAttrName) {
                label = data.value
            } else {
                coerceDataValue(
                    attrType = attrType,
                    rawValue = data.value,
                    phase = GraphIoPhase.READ_VERTEX,
                    elementName = "node",
                    recordId = nodeId,
                    columnName = attrName,
                    location = data.location,
                    sink = sink,
                )?.let { props[attrName] = it }
            }
        }
        sink.onVertex(GraphIoVertexRecord(externalId = nodeId, label = label, properties = props))
    }

    private fun parseEdge(
        reader: XMLStreamReader,
        keyIdToName: Map<String, String>,
        keyIdToType: Map<String, GraphMlAttrType>,
        options: GraphMlImportOptions,
        sink: GraphMlRecordSink,
    ) {
        val edgeId = reader.getAttributeValue(null, "id")
        val source = reader.getAttributeValue(null, "source")
        val target = reader.getAttributeValue(null, "target")
        if (reader.getAttributeValue(null, "directed") == "false") {
            recordUnsupportedElement(
                reader,
                options,
                sink,
                "GraphML undirected edges are not supported",
                GraphIoPhase.READ_EDGE,
            )
        }
        if (source.isNullOrBlank() || target.isNullOrBlank()) {
            sink.onFailure(
                GraphIoFailure(
                    phase = GraphIoPhase.READ_EDGE,
                    fileRole = GraphIoFileRole.UNIFIED,
                    recordId = edgeId,
                    message = "Edge missing source/target",
                ),
            )
            return
        }

        val dataMap = readDataChildren(reader, "edge", options, sink)

        var label = options.defaultEdgeLabel
        val props = mutableMapOf<String, Any?>()
        for ((keyId, data) in dataMap) {
            val attrName = keyIdToName[keyId] ?: keyId
            val attrType = keyIdToType[keyId] ?: GraphMlAttrType.STRING
            if (attrName == options.labelAttrName) {
                label = data.value
            } else {
                coerceDataValue(
                    attrType = attrType,
                    rawValue = data.value,
                    phase = GraphIoPhase.READ_EDGE,
                    elementName = "edge",
                    recordId = edgeId,
                    columnName = attrName,
                    location = data.location,
                    sink = sink,
                )?.let { props[attrName] = it }
            }
        }
        sink.onEdge(
            GraphIoEdgeRecord(
                externalId = edgeId,
                label = label,
                fromExternalId = source,
                toExternalId = target,
                properties = props,
            ),
        )
    }

    private fun coerceDataValue(
        attrType: GraphMlAttrType,
        rawValue: String,
        phase: GraphIoPhase,
        elementName: String,
        recordId: String?,
        columnName: String,
        location: String?,
        sink: GraphMlRecordSink,
    ): Any? {
        val coerced = attrType.coerce(rawValue)
        if (attrType != GraphMlAttrType.STRING && coerced == rawValue) {
            sink.onFailure(
                GraphIoFailure(
                    phase = phase,
                    severity = GraphIoFailureSeverity.ERROR,
                    location = location,
                    fileRole = GraphIoFileRole.UNIFIED,
                    recordId = recordId,
                    columnName = columnName,
                    elementName = elementName,
                    message = "Invalid GraphML ${attrType.xmlName} value",
                ),
            )
            return null
        }
        return coerced
    }

    /** 현재 요소의 `<data key="...">text</data>` 자식들을 읽어 keyId→value 맵으로 반환한다. */
    private fun readDataChildren(
        reader: XMLStreamReader,
        parentLocalName: String,
        options: GraphMlImportOptions,
        sink: GraphMlRecordSink,
    ): Map<String, GraphMlDataValue> {
        val result = mutableMapOf<String, GraphMlDataValue>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.localName == "data") {
                        val key = reader.getAttributeValue(null, "key") ?: continue
                        val location = reader.location.lineNumber
                            .takeIf { it > 0 }
                            ?.let { "line:$it" }
                        val value = reader.elementText ?: ""
                        result[key] = GraphMlDataValue(value, location)
                    } else {
                        when (reader.localName) {
                            "graph" -> recordUnsupportedElement(
                                reader, options, sink, "Nested GraphML graphs are not supported"
                            )
                            "port" -> recordUnsupportedElement(reader, options, sink)
                        }
                        skipElement(reader, reader.localName)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == parentLocalName) break
                }
            }
        }
        return result
    }

    private data class GraphMlDataValue(
        val value: String,
        val location: String?,
    )

    private fun skipElement(reader: XMLStreamReader, localName: String) {
        var depth = 1
        while (reader.hasNext() && depth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> if (reader.localName == localName) depth++
                XMLStreamConstants.END_ELEMENT -> if (reader.localName == localName) depth--
            }
        }
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        private val phaseTracker = RecordPhaseTracker()

        val malformedRecordPhase: GraphIoPhase?
            get() = phaseTracker.malformedRecordPhase

        override fun read(): Int = super.read().also { byte ->
            if (byte >= 0) phaseTracker.accept(byte)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) {
                repeat(count) { index -> phaseTracker.accept(buffer[offset + index].toInt()) }
            }
            return count
        }

        override fun close() {
            // The caller-owned GraphImportSource/use scope owns the underlying stream.
        }
    }

    /**
     * record opening tag가 START_ELEMENT로 승격되기 전에 깨지면 StAX가 whitespace만
     * 보고할 수 있다. 속성, 식별자, parser message를 보존하지 않고 제한된 lexical
     * marker만 유지하여 실패 단계를 식별한다.
     */
    private class RecordPhaseTracker {
        private enum class State {
            TEXT,
            EXPECT_NAME,
            NAME,
            RECORD_ATTRIBUTES,
            SELF_CLOSING,
            MALFORMED_RECORD,
            OTHER_MARKUP,
        }

        private enum class RecordName(val phase: GraphIoPhase, val token: String) {
            NODE(GraphIoPhase.READ_VERTEX, "node"),
            EDGE(GraphIoPhase.READ_EDGE, "edge"),
            ;

            fun matches(index: Int, byte: Int): Boolean = index < token.length && token[index] == byte.toChar()
        }

        private var state = State.TEXT
        private var recordName: RecordName? = null
        private var nameIndex = 0
        private var activeRecordPhase: GraphIoPhase? = null
        private var attributeQuote: Int? = null

        val malformedRecordPhase: GraphIoPhase?
            get() = activeRecordPhase

        fun accept(byte: Int) {
            when (state) {
                State.TEXT -> if (byte == '<'.code) state = State.EXPECT_NAME
                State.EXPECT_NAME -> acceptExpectedNameByte(byte)
                State.NAME -> acceptNameByte(byte)
                State.RECORD_ATTRIBUTES -> acceptRecordAttributeByte(byte)
                State.SELF_CLOSING -> if (byte == '>'.code) clearRecord() else state = State.MALFORMED_RECORD
                State.MALFORMED_RECORD -> Unit
                State.OTHER_MARKUP -> if (byte == '>'.code) state = State.TEXT
            }
        }

        private fun acceptExpectedNameByte(byte: Int) {
            when (byte) {
                '/'.code, '?'.code, '!'.code -> state = State.OTHER_MARKUP
                'n'.code -> startName(RecordName.NODE)
                'e'.code -> startName(RecordName.EDGE)
                else -> state = State.OTHER_MARKUP
            }
        }

        private fun startName(name: RecordName) {
            recordName = name
            nameIndex = 1
            state = State.NAME
            activeRecordPhase = null
            attributeQuote = null
        }

        private fun acceptNameByte(byte: Int) {
            val name = recordName
            if (name == null) {
                state = State.OTHER_MARKUP
                return
            }
            if (nameIndex < name.token.length && name.matches(nameIndex, byte)) {
                nameIndex++
                if (nameIndex == name.token.length) {
                    activeRecordPhase = name.phase
                }
                return
            }
            if (nameIndex == name.token.length && isTagNameDelimiter(byte)) {
                activeRecordPhase = name.phase
                state = State.RECORD_ATTRIBUTES
                acceptRecordAttributeByte(byte)
            } else {
                state = State.OTHER_MARKUP
                recordName = null
                activeRecordPhase = null
            }
        }

        private fun acceptRecordAttributeByte(byte: Int) {
            attributeQuote?.let { quote ->
                if (byte == quote) attributeQuote = null
                return
            }
            when (byte) {
                '"'.code, '\''.code -> attributeQuote = byte
                '>'.code -> clearRecord()
                '/'.code -> state = State.SELF_CLOSING
                '<'.code -> state = State.MALFORMED_RECORD
            }
        }

        private fun clearRecord() {
            state = State.TEXT
            recordName = null
            activeRecordPhase = null
            attributeQuote = null
        }

        private fun isTagNameDelimiter(byte: Int): Boolean =
            byte == '>'.code || byte == '/'.code || byte == ' '.code || byte == '\t'.code ||
                byte == '\r'.code || byte == '\n'.code

    }

    companion object : KLogging() {
        private val factory: XMLInputFactory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
    }
}

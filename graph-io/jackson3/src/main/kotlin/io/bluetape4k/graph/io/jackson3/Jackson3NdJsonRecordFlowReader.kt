package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.contract.GraphRecordFlowReader
import io.bluetape4k.graph.io.jackson3.internal.Jackson3EnvelopeCodec
import io.bluetape4k.graph.io.jackson3.internal.Jackson3ParsedRecord
import io.bluetape4k.graph.io.jackson3.internal.Jackson3RecordParser
import io.bluetape4k.graph.io.jackson3.internal.NdJsonEnvelope
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Jackson3 NDJSON의 정점/간선 envelope를 cold [Flow]로 읽는 reader.
 *
 * collect 시점에 source를 열고 한 줄씩 순차 방출한다. [PathSource]와
 * [InputStreamSource.closeInput]이 `true`인 source는 reader가 닫으며,
 * caller-owned source는 닫지 않는다. InputStream source는 one-shot이므로
 * 같은 source를 다시 수집하려면 호출자가 새 stream을 제공해야 한다.
 * 정점과 간선 Flow는 입력 순서를 유지하고, JSON parse failure는 raw payload나
 * codec message 없이 [GraphIoReadException]의 line location으로 전달한다.
 */
class Jackson3NdJsonRecordFlowReader(
    private val defaultVertexLabel: String = "Vertex",
    private val defaultEdgeLabel: String = "Edge",
) : GraphRecordFlowReader<GraphImportSource> {

    init {
        defaultVertexLabel.requireNotBlank("defaultVertexLabel")
        defaultEdgeLabel.requireNotBlank("defaultEdgeLabel")
    }

    private val parser = Jackson3RecordParser()
    private val codec = Jackson3EnvelopeCodec()

    override fun readVertices(source: GraphImportSource): Flow<GraphIoVertexRecord> =
        parser.records(source)
            .filter { it.envelope.type == NdJsonEnvelope.TYPE_VERTEX }
            .map(::toVertex)

    override fun readEdges(source: GraphImportSource): Flow<GraphIoEdgeRecord> =
        parser.records(source, GraphIoPhase.READ_EDGE)
            .filter { it.envelope.type == NdJsonEnvelope.TYPE_EDGE }
            .map(::toEdge)

    private fun toVertex(parsed: Jackson3ParsedRecord): GraphIoVertexRecord = try {
        codec.toVertex(parsed.envelope, defaultVertexLabel)
    } catch (_: IllegalArgumentException) {
        throw GraphIoReadException(
            GraphIoFailure(
                phase = GraphIoPhase.READ_VERTEX,
                fileRole = GraphIoFileRole.UNIFIED,
                location = "line:${parsed.lineNumber}",
                message = if (parsed.envelope.id == null) {
                    "Invalid vertex envelope: missing id"
                } else {
                    "Invalid vertex envelope"
                },
            ),
        )
    }

    private fun toEdge(parsed: Jackson3ParsedRecord): GraphIoEdgeRecord = try {
        codec.toEdge(parsed.envelope, defaultEdgeLabel)
    } catch (_: IllegalArgumentException) {
        throw GraphIoReadException(
            GraphIoFailure(
                phase = GraphIoPhase.READ_EDGE,
                fileRole = GraphIoFileRole.UNIFIED,
                location = "line:${parsed.lineNumber}",
                message = "Invalid edge envelope",
            ),
        )
    }
}

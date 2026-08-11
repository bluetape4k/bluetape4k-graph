package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphRecordFlowReader
import io.bluetape4k.graph.io.graphml.internal.StaxGraphMlReader
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.GraphIoPaths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * GraphML의 node/edge를 StAX로 순차 방출하는 cold [Flow] reader.
 *
 * collect 시점에 source를 열고 XML 요소를 한 건씩 방출한다. Path source와
 * `InputStreamSource(closeInput = true)`는 reader가 닫고, caller-owned stream은
 * 닫지 않는다. InputStream source는 one-shot이므로 같은 source를 다시 읽으려면
 * 호출자가 새 stream을 제공해야 한다. XML/typed-data ERROR는 raw XML 예외 대신
 * [GraphIoReadException]의 안전한 고정 메시지로 전달한다.
 */
class GraphMlRecordFlowReader(
    private val options: GraphMlImportOptions = GraphMlImportOptions(),
) : GraphRecordFlowReader<GraphImportSource> {

    private val reader = StaxGraphMlReader()

    override fun readVertices(source: GraphImportSource): Flow<GraphIoVertexRecord> =
        stream(source, vertexMapper = { it }, edgeMapper = { null })

    override fun readEdges(source: GraphImportSource): Flow<GraphIoEdgeRecord> =
        stream(source, vertexMapper = { null }, edgeMapper = { it })

    private fun <T : Any> stream(
        source: GraphImportSource,
        vertexMapper: (GraphIoVertexRecord) -> T?,
        edgeMapper: (GraphIoEdgeRecord) -> T?,
    ): Flow<T> {
        val events = kotlinx.coroutines.flow.flow {
            GraphIoPaths.openInputStream(source).use { input ->
                emitAll(reader.events(input, options))
            }
        }
        return events
            .map { event ->
                when (event) {
                    is StaxGraphMlReader.GraphMlRecordEvent.Vertex -> vertexMapper(event.record)
                    is StaxGraphMlReader.GraphMlRecordEvent.Edge -> edgeMapper(event.record)
                    is StaxGraphMlReader.GraphMlRecordEvent.Failure -> {
                        if (event.failure.severity == GraphIoFailureSeverity.ERROR) {
                            throw GraphIoReadException(event.failure)
                        }
                        null
                    }
                }
            }
            .filterNotNull()
            .buffer(0)
    }
}

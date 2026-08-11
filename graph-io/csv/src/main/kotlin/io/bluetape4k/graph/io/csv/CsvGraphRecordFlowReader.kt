package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.Record
import io.bluetape4k.graph.io.contract.GraphRecordFlowReader
import io.bluetape4k.graph.io.csv.internal.CsvRecordParser
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * CSV 정점/간선 파일을 cold [Flow]로 순차 읽는 reader.
 *
 * collect 시 source를 열고, [GraphImportSource.PathSource]와 owned input stream은 닫는다.
 * caller-owned input stream은 닫지 않는다. 각 Flow는 source를 한 번 순회하며,
 * one-shot stream을 다시 읽으려면 새 source를 제공해야 한다.
 */
class CsvGraphRecordFlowReader(
    private val csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
) : GraphRecordFlowReader<CsvGraphImportSource> {

    private val parser = CsvRecordParser()
    private val codec = io.bluetape4k.graph.io.csv.internal.CsvRecordCodec(csvOptions.propertyMode)

    override fun readVertices(source: CsvGraphImportSource): Flow<GraphIoVertexRecord> =
        parser.records(
            source = source.vertices,
            phase = GraphIoPhase.READ_VERTEX,
            fileRole = GraphIoFileRole.VERTICES,
        ).map(::toVertex)

    override fun readEdges(source: CsvGraphImportSource): Flow<GraphIoEdgeRecord> =
        parser.records(
            source = source.edges,
            phase = GraphIoPhase.READ_EDGE,
            fileRole = GraphIoFileRole.EDGES,
        ).map(::toEdge)

    private fun toVertex(record: Record): GraphIoVertexRecord {
        val externalId = record.getString("id").orEmpty()
        if (externalId.isBlank()) {
            throw GraphIoReadException(
                failure = GraphIoFailure(
                    phase = GraphIoPhase.READ_VERTEX,
                    fileRole = GraphIoFileRole.VERTICES,
                    location = "row:${record.rowNumber}",
                    message = "CSV vertex id is blank",
                ),
            )
        }
        val label = record.getString("label").orEmpty().ifBlank { "Vertex" }
        return GraphIoVertexRecord(
            externalId = externalId,
            label = label,
            properties = codec.extractProperties(record.toColumnMap()),
        )
    }

    private fun toEdge(record: Record): GraphIoEdgeRecord {
        val from = record.getString("from").orEmpty()
        val to = record.getString("to").orEmpty()
        if (from.isBlank() || to.isBlank()) {
            throw GraphIoReadException(
                failure = GraphIoFailure(
                    phase = GraphIoPhase.READ_EDGE,
                    fileRole = GraphIoFileRole.EDGES,
                    location = "row:${record.rowNumber}",
                    message = "CSV edge endpoint is blank",
                ),
            )
        }
        return GraphIoEdgeRecord(
            externalId = record.getString("id")?.takeIf { it.isNotBlank() },
            label = record.getString("label").orEmpty().ifBlank { "Edge" },
            fromExternalId = from,
            toExternalId = to,
            properties = codec.extractProperties(record.toColumnMap()),
        )
    }

}

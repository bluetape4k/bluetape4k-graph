package io.bluetape4k.graph.io.okio

import io.bluetape4k.graph.io.contract.GraphRecordFlowReader
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.csv.CsvGraphIoOptions
import io.bluetape4k.graph.io.csv.CsvGraphRecordFlowReader
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.graphml.GraphMlRecordFlowReader
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonRecordFlowReader
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonRecordFlowReader
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.okio.bridge.toInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okio.Path.Companion.toPath
import java.io.ByteArrayInputStream

/**
 * OkIO source를 공통 [GraphRecordFlowReader] 계약으로 연결하는 cold reader.
 *
 * 단일 스트림 포맷은 collect 시 [GraphIoOkioPaths.openSource]로 source를 열고,
 * delegate가 underlying OkIO source를 닫지 않는 `closeInput = false` bridge를
 * 사용한 뒤 outer scope가 정확히 한 번 닫는다. `PathSource`와 owns 플래그가
 * `true`인 source는 library가 닫고, caller-owned source는 닫지 않는다.
 * CSV는 정점/간선 파일 쌍이 필요하므로 [OkioGraphImportSource.PathSource]만
 * 허용하며, stream-backed CSV에는 명시적인 [UnsupportedOperationException]을
 * 던진다. InputStream/Source 기반 source는 one-shot이므로 재수집에는 새 source가 필요하다.
 */
class OkioGraphRecordFlowReader(
    private val format: GraphIoFormat,
    private val csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    private val graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
) : GraphRecordFlowReader<OkioGraphImportSource> {

    private val jackson2Reader = Jackson2NdJsonRecordFlowReader()
    private val jackson3Reader = Jackson3NdJsonRecordFlowReader()
    private val graphMlReader = GraphMlRecordFlowReader(graphMlOptions)
    private val csvReader = CsvGraphRecordFlowReader(csvOptions)

    override fun readVertices(source: OkioGraphImportSource): Flow<GraphIoVertexRecord> = when (format) {
        GraphIoFormat.CSV -> readCsv(source, vertices = true)
        GraphIoFormat.NDJSON_JACKSON2 -> readSingleStream(source, jackson2Reader::readVertices)
        GraphIoFormat.NDJSON_JACKSON3 -> readSingleStream(source, jackson3Reader::readVertices)
        GraphIoFormat.GRAPHML -> readSingleStream(source, graphMlReader::readVertices)
    }

    override fun readEdges(source: OkioGraphImportSource): Flow<GraphIoEdgeRecord> = when (format) {
        GraphIoFormat.CSV -> readCsv(source, vertices = false)
        GraphIoFormat.NDJSON_JACKSON2 -> readSingleStream(source, jackson2Reader::readEdges)
        GraphIoFormat.NDJSON_JACKSON3 -> readSingleStream(source, jackson3Reader::readEdges)
        GraphIoFormat.GRAPHML -> readSingleStream(source, graphMlReader::readEdges)
    }

    private inline fun <T> readSingleStream(
        source: OkioGraphImportSource,
        crossinline read: (GraphImportSource) -> Flow<T>,
    ): Flow<T> = flow {
        GraphIoOkioPaths.openSource(source).use { bufferedSource ->
            emitAll(
                read(
                    GraphImportSource.InputStreamSource(
                        input = bufferedSource.toInputStream(),
                        closeInput = false,
                    ),
                ),
            )
        }
    }

    private fun <T> readCsv(
        source: OkioGraphImportSource,
        vertices: Boolean,
    ): Flow<T> {
        val pathSource = source as? OkioGraphImportSource.PathSource
            ?: throw UnsupportedOperationException(
                "CSV requires two files for vertices and edges, so " +
                    "OkioGraphRecordFlowReader supports only PathSource. " +
                    "Use CsvGraphRecordFlowReader directly for stream-backed sources.",
            )
        val stem = pathSource.path.toString().removeSuffix(".csv")
        val path = "${stem}_${if (vertices) "vertices" else "edges"}.csv".toPath()
        return flow {
            GraphIoOkioPaths.openSource(
                OkioGraphImportSource.PathSource(path, pathSource.fileSystem),
            ).use { bufferedSource ->
                val inputSource = GraphImportSource.InputStreamSource(
                    input = bufferedSource.toInputStream(),
                    closeInput = false,
                )
                val emptySource = GraphImportSource.InputStreamSource(
                    input = ByteArrayInputStream(ByteArray(0)),
                    closeInput = false,
                )
                val csvSource = if (vertices) {
                    CsvGraphImportSource(vertices = inputSource, edges = emptySource)
                } else {
                    CsvGraphImportSource(vertices = emptySource, edges = inputSource)
                }
                @Suppress("UNCHECKED_CAST")
                if (vertices) {
                    emitAll(csvReader.readVertices(csvSource) as Flow<T>)
                } else {
                    emitAll(csvReader.readEdges(csvSource) as Flow<T>)
                }
            }
        }
    }
}

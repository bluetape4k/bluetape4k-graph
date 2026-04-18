package io.bluetape4k.graph.io.report

import io.bluetape4k.logging.KLogging
import java.io.Serializable
import java.time.Duration

/** 그래프 익스포트 결과 보고서 */
data class GraphExportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesWritten: Long,
    val edgesWritten: Long,
    val skippedVertices: Long = 0,
    val skippedEdges: Long = 0,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable {
    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

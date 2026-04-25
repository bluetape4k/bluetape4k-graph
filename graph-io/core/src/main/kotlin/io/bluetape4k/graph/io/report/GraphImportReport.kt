package io.bluetape4k.graph.io.report

import io.bluetape4k.logging.KLogging
import java.io.Serializable
import java.time.Duration

/** 그래프 임포트 결과 보고서 */
data class GraphImportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable {
    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

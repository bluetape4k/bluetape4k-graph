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
    val skippedVertices: Long = 0,
    val skippedEdges: Long = 0,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable {
    init {
        require(verticesRead >= 0) { "verticesRead must be >= 0" }
        require(verticesCreated >= 0) { "verticesCreated must be >= 0" }
        require(edgesRead >= 0) { "edgesRead must be >= 0" }
        require(edgesCreated >= 0) { "edgesCreated must be >= 0" }
        require(skippedVertices >= 0) { "skippedVertices must be >= 0" }
        require(skippedEdges >= 0) { "skippedEdges must be >= 0" }
    }

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L
    }
}

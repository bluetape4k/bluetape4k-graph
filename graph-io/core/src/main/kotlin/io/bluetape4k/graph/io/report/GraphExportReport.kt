package io.bluetape4k.graph.io.report

import java.io.Serializable
import java.time.Duration

/**
 * 그래프 익스포트 결과 보고서.
 *
 * `failures`가 비어 있으면 [status]는 [GraphIoStatus.COMPLETED], 부분 실패가 있으면 [GraphIoStatus.PARTIAL]이다.
 *
 * ```kotlin
 * val report = exporter.exportGraph(sink = ..., operations = ops, options = opts)
 * println("${report.verticesWritten}V ${report.edgesWritten}E (${report.status}) in ${report.elapsed}")
 * if (report.failures.isNotEmpty()) println("실패: ${report.failures}")
 * ```
 *
 * @property status 익스포트 완료 상태.
 * @property format 사용된 직렬화 포맷.
 * @property verticesWritten 성공적으로 출력된 정점 수. 0 이상.
 * @property edgesWritten 성공적으로 출력된 간선 수. 0 이상.
 * @property skippedVertices 건너뛴 정점 수 (필터 또는 오류). 0 이상.
 * @property skippedEdges 건너뛴 간선 수 (필터 또는 오류). 0 이상.
 * @property elapsed 익스포트 소요 시간.
 * @property failures 발생한 오류 목록. 비어 있으면 완전 성공.
 */
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
    init {
        require(verticesWritten >= 0) { "verticesWritten must be >= 0" }
        require(edgesWritten >= 0) { "edgesWritten must be >= 0" }
        require(skippedVertices >= 0) { "skippedVertices must be >= 0" }
        require(skippedEdges >= 0) { "skippedEdges must be >= 0" }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

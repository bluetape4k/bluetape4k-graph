package io.bluetape4k.graph.io.report

import io.bluetape4k.logging.KLogging
import java.io.Serializable
import java.time.Duration

/**
 * 그래프 임포트 결과 보고서.
 *
 * `failures`가 비어 있으면 [status]는 [GraphIoStatus.COMPLETED], 부분 실패가 있으면 [GraphIoStatus.PARTIAL]이다.
 *
 * ```kotlin
 * val report = importer.importGraph(source = ..., operations = ops, options = opts)
 * println("${report.verticesCreated}V ${report.edgesCreated}E (${report.status}) in ${report.elapsed}")
 * if (report.failures.isNotEmpty()) println("실패: ${report.failures}")
 * ```
 *
 * @property status 임포트 완료 상태.
 * @property format 사용된 직렬화 포맷.
 * @property verticesRead 소스에서 읽은 정점 수. 0 이상.
 * @property verticesCreated 실제로 생성된 정점 수. 0 이상, [verticesRead] 이하.
 * @property edgesRead 소스에서 읽은 간선 수. 0 이상.
 * @property edgesCreated 실제로 생성된 간선 수. 0 이상, [edgesRead] 이하.
 * @property skippedVertices 건너뛴 정점 수 (중복 정책 또는 오류). 0 이상.
 * @property skippedEdges 건너뛴 간선 수 (중복 정책 또는 오류). 0 이상.
 * @property elapsed 임포트 소요 시간.
 * @property failures 발생한 오류 목록. 비어 있으면 완전 성공.
 */
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

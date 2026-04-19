package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport

/**
 * Kotlin 코루틴 기반 suspend 벌크 익스포터 계약.
 *
 * [GraphBulkExporter]와 동일한 의미론을 가지지만 [GraphSuspendOperations]를 사용하여
 * 코루틴 컨텍스트를 유지한 채 non-blocking 방식으로 그래프를 읽는다.
 * I/O 작업은 일반적으로 `Dispatchers.IO`에서 수행된다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.SuspendJackson3NdJsonBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import kotlinx.coroutines.runBlocking
 * import java.nio.file.Paths
 *
 * val exporter: GraphSuspendBulkExporter<GraphExportSink> = SuspendJackson3NdJsonBulkExporter()
 * val report = runBlocking {
 *     exporter.exportGraphSuspending(
 *         sink = GraphExportSink.PathSink(Paths.get("graph.ndjson")),
 *         operations = suspendGraphOps,
 *         options = GraphExportOptions(vertexLabels = setOf("Person")),
 *     )
 * }
 * println("exported ${report.verticesWritten} vertices in ${report.elapsed}")
 * ```
 *
 * @see GraphBulkExporter 동기 변형
 * @see GraphVirtualThreadBulkExporter Virtual Thread 비동기 변형
 */
interface GraphSuspendBulkExporter<T : Any> {

    /**
     * 주어진 [sink]로 그래프 데이터를 코루틴 방식으로 익스포트한다.
     *
     * @param sink 포맷별 출력 대상.
     * @param operations 익스포트 대상 그래프에 대한 suspend 읽기 API.
     * @param options 레이블 필터 및 빈 속성 포함 여부 등의 익스포트 옵션.
     * @return 쓰여진 정점/간선 수, 경과 시간, 실패 목록을 담은 [GraphExportReport].
     */
    suspend fun exportGraphSuspending(
        sink: T,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}

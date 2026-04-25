package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport

/**
 * Kotlin 코루틴 기반 suspend 벌크 임포터 계약.
 *
 * [GraphBulkImporter]와 동일한 의미론을 가지지만 [GraphSuspendOperations]를 사용하여
 * 코루틴 컨텍스트를 유지한 채 non-blocking 방식으로 그래프에 쓰기를 수행한다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.SuspendJackson3NdJsonBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.options.MissingEndpointPolicy
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import kotlinx.coroutines.runBlocking
 * import java.nio.file.Paths
 *
 * val importer: GraphSuspendBulkImporter<GraphImportSource> = SuspendJackson3NdJsonBulkImporter()
 * val report = runBlocking {
 *     importer.importGraphSuspending(
 *         source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *         operations = suspendGraphOps,
 *         options = GraphImportOptions(onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE),
 *     )
 * }
 * println("${report.edgesCreated} edges created (skipped ${report.skippedEdges})")
 * ```
 *
 * @see GraphBulkImporter 동기 변형
 * @see GraphVirtualThreadBulkImporter Virtual Thread 비동기 변형
 */
interface GraphSuspendBulkImporter<S : Any> {

    /**
     * 주어진 [source]로부터 그래프 데이터를 코루틴 방식으로 임포트한다.
     *
     * @param source 포맷별 입력 소스.
     * @param operations 임포트 대상 그래프에 대한 suspend 쓰기 API.
     * @param options 중복/미존재 엔드포인트 정책, 기본 레이블, 배치 크기 등.
     * @return 읽어들인/생성된 정점·간선 수, 경과 시간, 실패 목록을 담은 [GraphImportReport].
     */
    suspend fun importGraphSuspending(
        source: S,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}

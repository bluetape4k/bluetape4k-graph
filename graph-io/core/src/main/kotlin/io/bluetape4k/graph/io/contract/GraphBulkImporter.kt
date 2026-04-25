package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport

/**
 * 공통 동기(blocking) 벌크 임포터 계약.
 *
 * 모든 포맷별 임포터(`CsvGraphBulkImporter`, `Jackson3NdJsonBulkImporter`, `GraphMlBulkImporter` 등)가
 * 이 인터페이스를 구현하여 동일한 호출 규약을 제공한다.
 *
 * ### 타입 파라미터
 * - `S` : 포맷별 소스 타입. 단일 파일 포맷은 [io.bluetape4k.graph.io.source.GraphImportSource],
 *   CSV처럼 정점/간선 파일을 분리하는 포맷은 `CsvGraphImportSource`를 사용한다.
 *
 * ### 임포트 동작
 * - 정점을 먼저 생성하여 외부 ID → 백엔드 ID 매핑을 [io.bluetape4k.graph.io.support.GraphIoExternalIdMap]에 누적한다.
 * - 간선은 [GraphImportOptions.maxEdgeBufferSize]까지 버퍼링한 뒤, 참조 정점이 확보되면 일괄 생성한다.
 * - 중복 정점이나 미존재 엔드포인트 처리 방식은 [GraphImportOptions.onDuplicateVertexId] /
 *   [GraphImportOptions.onMissingEdgeEndpoint]로 제어한다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
 * import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.options.MissingEndpointPolicy
 * import io.bluetape4k.graph.io.report.GraphIoStatus
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer: GraphBulkImporter<GraphImportSource> = Jackson3NdJsonBulkImporter()
 * val report = importer.importGraph(
 *     source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphImportOptions(
 *         onDuplicateVertexId = DuplicateVertexPolicy.SKIP,
 *         onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE,
 *     ),
 * )
 * require(report.status != GraphIoStatus.FAILED) { "Import failed: ${report.failures}" }
 * ```
 *
 * @see GraphSuspendBulkImporter 코루틴 suspend 변형
 * @see GraphVirtualThreadBulkImporter Virtual Thread 비동기 변형
 */
interface GraphBulkImporter<S : Any> {

    /**
     * 주어진 [source]로부터 그래프 데이터를 동기 방식으로 임포트한다.
     *
     * @param source 포맷별 입력 소스. `PathSource`는 파일을 열고, `InputStreamSource`는
     *               `closeInput = false`(기본)인 경우 호출자가 스트림 소유권을 유지한다.
     * @param operations 임포트 대상 그래프에 대한 쓰기 API.
     * @param options 중복/미존재 엔드포인트 정책, 기본 레이블, 배치 크기 등.
     * @return 읽어들인/생성된 정점·간선 수, 경과 시간, 실패 목록을 담은 [GraphImportReport].
     */
    fun importGraph(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}

package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport

/**
 * 공통 동기(blocking) 벌크 익스포터 계약.
 *
 * 모든 포맷별 익스포터(`CsvGraphBulkExporter`, `Jackson3NdJsonBulkExporter`, `GraphMlBulkExporter` 등)가
 * 이 인터페이스를 구현하여 동일한 호출 규약을 제공한다.
 *
 * ### 타입 파라미터
 * - `T` : 포맷별 싱크 타입. 단일 파일 포맷은 [io.bluetape4k.graph.io.source.GraphExportSink],
 *   CSV처럼 정점/간선 파일을 분리하는 포맷은 `CsvGraphExportSink`를 사용한다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter: GraphBulkExporter<GraphExportSink> = Jackson3NdJsonBulkExporter()
 * val report = exporter.exportGraph(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphExportOptions(
 *         vertexLabels = setOf("Person"),
 *         edgeLabels = setOf("KNOWS"),
 *     ),
 * )
 * println("${report.verticesWritten} vertices, ${report.edgesWritten} edges (${report.status})")
 * ```
 *
 * @see GraphSuspendBulkExporter 코루틴 suspend 변형
 * @see GraphVirtualThreadBulkExporter Virtual Thread 비동기 변형
 */
interface GraphBulkExporter<T : Any> {

    /**
     * 주어진 [sink]로 그래프 데이터를 동기 방식으로 익스포트한다.
     *
     * @param sink 포맷별 출력 대상. `PathSink`는 부모 디렉터리를 자동 생성하며,
     *             `OutputStreamSink`는 기본적으로 호출자가 스트림 소유권을 유지한다.
     * @param operations 익스포트 대상 그래프에 대한 읽기 API.
     * @param options 레이블 필터 및 빈 속성 포함 여부 등의 익스포트 옵션.
     * @return 쓰여진 정점/간선 수, 경과 시간, 발생한 실패 목록을 담은 [GraphExportReport].
     *         `report.status`가 `PARTIAL`이면 일부 레코드가 실패한 것이며 `report.failures`를 확인한다.
     */
    fun exportGraph(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}

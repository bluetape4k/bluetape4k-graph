package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import java.util.concurrent.CompletableFuture

/**
 * Java 21+ Virtual Thread 기반 비동기 벌크 익스포터 계약.
 *
 * [GraphBulkExporter]와 동일한 의미론을 가지지만 작업을 Virtual Thread에서 실행하고
 * [CompletableFuture]로 결과를 반환한다. 블로킹 I/O가 많은 익스포트 작업을 경량 스레드로
 * 위임하려 할 때 사용한다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkExporter
 * import io.bluetape4k.graph.io.options.GraphExportOptions
 * import io.bluetape4k.graph.io.source.GraphExportSink
 * import java.nio.file.Paths
 *
 * val exporter: GraphVirtualThreadBulkExporter<GraphExportSink> =
 *     Jackson3NdJsonVirtualThreadBulkExporter()
 * val future = exporter.exportGraphAsync(
 *     sink = GraphExportSink.PathSink(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphExportOptions(vertexLabels = setOf("Person")),
 * )
 * val report = future.join()  // 또는 future.thenAccept { ... }
 * ```
 *
 * 구현은 보통 기존 동기 익스포터를 [io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter.wrapExporter]로
 * 래핑하는 방식으로 제공된다.
 *
 * @see GraphBulkExporter 동기 변형
 * @see GraphSuspendBulkExporter 코루틴 suspend 변형
 */
interface GraphVirtualThreadBulkExporter<T : Any> {

    /**
     * 주어진 [sink]로 그래프 데이터를 Virtual Thread에서 비동기로 익스포트한다.
     *
     * @param sink 포맷별 출력 대상.
     * @param operations 익스포트 대상 그래프에 대한 읽기 API.
     * @param options 레이블 필터 및 빈 속성 포함 여부 등의 익스포트 옵션.
     * @return 완료 시 [GraphExportReport]를 담는 [CompletableFuture].
     *         예외는 future를 통해 전파된다(`CompletionException`으로 래핑될 수 있음).
     */
    fun exportGraphAsync(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): CompletableFuture<GraphExportReport>
}

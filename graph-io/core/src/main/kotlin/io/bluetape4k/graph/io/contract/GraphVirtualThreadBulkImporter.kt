package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import java.util.concurrent.CompletableFuture

/**
 * Java 21+ Virtual Thread 기반 비동기 벌크 임포터 계약.
 *
 * [GraphBulkImporter]와 동일한 의미론을 가지지만 작업을 Virtual Thread에서 실행하고
 * [CompletableFuture]로 결과를 반환한다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkImporter
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer: GraphVirtualThreadBulkImporter<GraphImportSource> =
 *     Jackson3NdJsonVirtualThreadBulkImporter()
 * val future = importer.importGraphAsync(
 *     source = GraphImportSource.PathSource(Paths.get("graph.ndjson")),
 *     operations = graphOps,
 *     options = GraphImportOptions(),
 * )
 * val report = future.join()
 * ```
 *
 * 구현은 보통 기존 동기 임포터를 [io.bluetape4k.graph.io.support.VirtualThreadGraphBulkAdapter.wrapImporter]로
 * 래핑하는 방식으로 제공된다.
 *
 * @see GraphBulkImporter 동기 변형
 * @see GraphSuspendBulkImporter 코루틴 suspend 변형
 */
interface GraphVirtualThreadBulkImporter<S : Any> {

    /**
     * 주어진 [source]로부터 그래프 데이터를 Virtual Thread에서 비동기로 임포트한다.
     *
     * @param source 포맷별 입력 소스.
     * @param operations 임포트 대상 그래프에 대한 쓰기 API.
     * @param options 중복/미존재 엔드포인트 정책, 기본 레이블, 배치 크기 등.
     * @return 완료 시 [GraphImportReport]를 담는 [CompletableFuture].
     *         예외는 future를 통해 전파된다(`CompletionException`으로 래핑될 수 있음).
     */
    fun importGraphAsync(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): CompletableFuture<GraphImportReport>
}

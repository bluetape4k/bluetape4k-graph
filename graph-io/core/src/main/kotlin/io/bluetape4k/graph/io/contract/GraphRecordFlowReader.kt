package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import kotlinx.coroutines.flow.Flow

/**
 * 포맷 내부에서 raw 레코드만 방출하는 헬퍼 계약.
 *
 * 벌크 임포터를 직접 구성하지 않고 포맷 파서의 출력을 재사용하고 싶을 때 사용한다.
 * 엣지 레코드의 [GraphIoEdgeRecord.fromExternalId] / [GraphIoEdgeRecord.toExternalId]는
 * **아직 resolve되지 않은 외부 ID**이며, 엔드포인트 resolve는 bulk importer의 책임이다.
 *
 * ### 사용 예시
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import kotlinx.coroutines.flow.take
 * import kotlinx.coroutines.flow.toList
 * import kotlinx.coroutines.runBlocking
 * import java.nio.file.Paths
 *
 * val reader: GraphRecordFlowReader<GraphImportSource> = /* 포맷별 구현 */
 * val source = GraphImportSource.PathSource(Paths.get("graph.ndjson"))
 *
 * // 정점 미리보기
 * val first10 = runBlocking { reader.readVertices(source).take(10).toList() }
 *
 * // 간선 스캔
 * val edgeCount = runBlocking {
 *     var n = 0L
 *     reader.readEdges(source).collect { _ -> n++ }
 *     n
 * }
 * ```
 */
interface GraphRecordFlowReader<S : Any> {

    /** [source]에서 정점 레코드를 순차적으로 방출하는 [Flow]를 반환한다. */
    fun readVertices(source: S): Flow<GraphIoVertexRecord>

    /**
     * [source]에서 간선 레코드를 순차적으로 방출하는 [Flow]를 반환한다.
     * 방출된 레코드의 `fromExternalId`/`toExternalId`는 아직 resolve되지 않았다.
     */
    fun readEdges(source: S): Flow<GraphIoEdgeRecord>
}

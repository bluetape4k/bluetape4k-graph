package io.bluetape4k.graph.io.contract

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import kotlinx.coroutines.flow.Flow

/**
 * 포맷 내부에서 raw 레코드만 방출하는 헬퍼 계약.
 * 엣지 레코드의 `fromExternalId`/`toExternalId`는 아직 resolve되지 않은 외부 ID이다.
 * 엔드포인트 resolve는 bulk importer 책임이다.
 */
interface GraphRecordFlowReader<S : Any> {
    fun readVertices(source: S): Flow<GraphIoVertexRecord>
    fun readEdges(source: S): Flow<GraphIoEdgeRecord>
}

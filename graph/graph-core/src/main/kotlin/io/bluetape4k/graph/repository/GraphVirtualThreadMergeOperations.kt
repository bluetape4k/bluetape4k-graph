package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread에서 graph merge/upsert를 실행하는 optional surface다.
 *
 * [GraphMergeOperations]를 구현한 backend에만 adapter가 이 surface를 지원한다.
 * 각 작업은 하나의 virtual thread에서 동기 merge를 실행하고 결과를
 * [CompletableFuture]로 전달한다. 예외는 future의 exceptional completion으로
 * 전파되며, `cancel(true)`와 `orTimeout`은 표준 [CompletableFuture] 계약을 따른다.
 * delegate의 수명은 호출자가 소유한다.
 */
interface GraphVirtualThreadMergeOperations {

    fun mergeVertexAsync(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<GraphVertex>

    fun mergeEdgeAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?> = emptyMap(),
        setProperties: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<GraphEdge>
}

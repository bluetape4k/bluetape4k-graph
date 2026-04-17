package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread 기반 그래프 간선(Edge) CRUD 저장소.
 *
 * 모든 메서드는 동기 [GraphEdgeRepository] 를 Virtual Thread 위에서 실행한
 * 결과를 `CompletableFuture<T>` 로 반환한다. Java interop 목적이다.
 */
interface GraphVirtualThreadEdgeRepository {

    fun createEdgeAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<GraphEdge>

    fun findEdgesByLabelAsync(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<List<GraphEdge>>

    fun deleteEdgeAsync(label: String, id: GraphElementId): CompletableFuture<Boolean>
}

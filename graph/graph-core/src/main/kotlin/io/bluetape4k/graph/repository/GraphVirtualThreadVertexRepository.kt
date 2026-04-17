package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread 기반 그래프 정점(Vertex) CRUD 저장소.
 *
 * 모든 메서드는 동기 [GraphVertexRepository] 를 Virtual Thread 위에서 실행한
 * 결과를 `CompletableFuture<T>` 로 반환한다. Java interop 및 CompletableFuture 파이프라인용이다.
 */
interface GraphVirtualThreadVertexRepository {

    fun createVertexAsync(
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<GraphVertex>

    fun findVertexByIdAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<GraphVertex?>

    fun findVerticesByLabelAsync(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
    ): CompletableFuture<List<GraphVertex>>

    fun updateVertexAsync(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex?>

    fun deleteVertexAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<Boolean>

    fun countVerticesAsync(label: String): CompletableFuture<Long>
}

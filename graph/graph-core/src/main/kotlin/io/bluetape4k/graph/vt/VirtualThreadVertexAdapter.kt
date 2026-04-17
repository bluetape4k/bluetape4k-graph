package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphVertexRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * [GraphVertexRepository] 의 모든 메서드를 Virtual Thread 위에서 실행하는 어댑터.
 *
 * 단일 작업에는 `virtualFutureOf { }` 를 사용한다.
 *
 * @param delegate 위임할 동기 [GraphVertexRepository].
 */
class VirtualThreadVertexAdapter(
    private val delegate: GraphVertexRepository,
) : GraphVirtualThreadVertexRepository {

    companion object : KLogging()

    override fun createVertexAsync(
        label: String,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex> =
        virtualFutureOf { delegate.createVertex(label, properties) }

    override fun findVertexByIdAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<GraphVertex?> =
        virtualFutureOfNullable { delegate.findVertexById(label, id) }

    override fun findVerticesByLabelAsync(
        label: String,
        filter: Map<String, Any?>,
    ): CompletableFuture<List<GraphVertex>> =
        virtualFutureOf { delegate.findVerticesByLabel(label, filter) }

    override fun updateVertexAsync(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphVertex?> =
        virtualFutureOfNullable { delegate.updateVertex(label, id, properties) }

    override fun deleteVertexAsync(
        label: String,
        id: GraphElementId,
    ): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.deleteVertex(label, id) }

    override fun countVerticesAsync(label: String): CompletableFuture<Long> =
        virtualFutureOf { delegate.countVertices(label) }
}

/**
 * [GraphVertexRepository] 를 Virtual Thread 정점 어댑터로 감싸는 확장 함수.
 */
fun GraphVertexRepository.asVirtualThreadVertexRepository(): GraphVirtualThreadVertexRepository =
    VirtualThreadVertexAdapter(this)

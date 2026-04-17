package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphEdgeRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * [GraphEdgeRepository] 의 모든 메서드를 Virtual Thread 위에서 실행하는 어댑터.
 *
 * 단일 작업에는 `virtualFutureOf { }` 를 사용한다.
 *
 * @param delegate 위임할 동기 [GraphEdgeRepository].
 */
class VirtualThreadEdgeAdapter(
    private val delegate: GraphEdgeRepository,
) : GraphVirtualThreadEdgeRepository {

    companion object : KLogging()

    override fun createEdgeAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): CompletableFuture<GraphEdge> =
        virtualFutureOf { delegate.createEdge(fromId, toId, label, properties) }

    override fun findEdgesByLabelAsync(
        label: String,
        filter: Map<String, Any?>,
    ): CompletableFuture<List<GraphEdge>> =
        virtualFutureOf { delegate.findEdgesByLabel(label, filter) }

    override fun deleteEdgeAsync(label: String, id: GraphElementId): CompletableFuture<Boolean> =
        virtualFutureOf { delegate.deleteEdge(label, id) }
}

/**
 * [GraphEdgeRepository] 를 Virtual Thread 어댑터로 감싸는 확장 함수.
 */
fun GraphEdgeRepository.asVirtualThreadEdge(): GraphVirtualThreadEdgeRepository =
    VirtualThreadEdgeAdapter(this)

package io.bluetape4k.graph.vt

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphTraversalRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * [GraphTraversalRepository] 의 모든 메서드를 Virtual Thread 위에서 실행하는 어댑터.
 *
 * 단일 작업에는 `virtualFutureOf { }` 를 사용한다.
 *
 * @param delegate 위임할 동기 [GraphTraversalRepository].
 */
class VirtualThreadTraversalAdapter(
    private val delegate: GraphTraversalRepository,
) : GraphVirtualThreadTraversalRepository {

    companion object : KLogging()

    override fun neighborsAsync(
        startId: GraphElementId,
        options: NeighborOptions,
    ): CompletableFuture<List<GraphVertex>> =
        virtualFutureOf { delegate.neighbors(startId, options) }

    override fun shortestPathAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): CompletableFuture<GraphPath?> =
        virtualFutureOfNullable { delegate.shortestPath(fromId, toId, options) }

    override fun allPathsAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): CompletableFuture<List<GraphPath>> =
        virtualFutureOf { delegate.allPaths(fromId, toId, options) }
}

/**
 * [GraphTraversalRepository] 를 Virtual Thread 순회 어댑터로 감싸는 확장 함수.
 */
fun GraphTraversalRepository.asVirtualThreadTraversal(): GraphVirtualThreadTraversalRepository =
    VirtualThreadTraversalAdapter(this)

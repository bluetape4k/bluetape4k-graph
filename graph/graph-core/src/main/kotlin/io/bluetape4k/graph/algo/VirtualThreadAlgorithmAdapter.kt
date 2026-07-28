package io.bluetape4k.graph.algo

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.logging.KLogging
import java.util.concurrent.CompletableFuture

/**
 * 모든 [GraphAlgorithmRepository] method를 virtual thread에서 실행하는 adapter.
 *
 * 단일 operation은 `virtualFutureOf { }`를 사용한다.
 *
 * ### Usage
 * ```kotlin
 * val ops: GraphOperations = Neo4jGraphOperations(driver)
 * val vtOps = ops.asVirtualThread()
 * val scores = vtOps.pageRankAsync().join()
 * ```
 *
 * @param delegate 위임 대상 synchronous [GraphAlgorithmRepository].
 */
class VirtualThreadAlgorithmAdapter(
    private val delegate: GraphAlgorithmRepository,
) : GraphVirtualThreadAlgorithmRepository {

    companion object : KLogging()

    override fun pageRankAsync(options: PageRankOptions): CompletableFuture<List<PageRankScore>> =
        virtualFutureOf { delegate.pageRank(options) }

    override fun degreeCentralityAsync(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): CompletableFuture<DegreeResult> =
        virtualFutureOf { delegate.degreeCentrality(vertexId, options) }

    override fun connectedComponentsAsync(options: ComponentOptions): CompletableFuture<List<GraphComponent>> =
        virtualFutureOf { delegate.connectedComponents(options) }

    override fun bfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions,
    ): CompletableFuture<List<TraversalVisit>> =
        virtualFutureOf { delegate.bfs(startId, options) }

    override fun dfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions,
    ): CompletableFuture<List<TraversalVisit>> =
        virtualFutureOf { delegate.dfs(startId, options) }

    override fun detectCyclesAsync(options: CycleOptions): CompletableFuture<List<GraphCycle>> =
        virtualFutureOf { delegate.detectCycles(options) }
}

/**
 * [GraphAlgorithmRepository]를 virtual-thread adapter로 감싼다.
 *
 * ```kotlin
 * val vtOps = ops.asVirtualThread()
 * val scores = vtOps.pageRankAsync().join()
 * ```
 */
fun GraphAlgorithmRepository.asVirtualThread(): GraphVirtualThreadAlgorithmRepository =
    VirtualThreadAlgorithmAdapter(this)

package io.bluetape4k.graph.algo

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [GraphAlgorithmRepository] 의 모든 메서드를 Virtual Thread 위에서 실행하는 어댑터.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = Neo4jGraphOperations(driver)
 * val vtOps = ops.asVirtualThread()
 * val future = vtOps.pageRankAsync()
 * val scores = future.join()
 * ```
 *
 * @param delegate 위임할 동기 [GraphAlgorithmRepository].
 * @param executor Virtual Thread executor. 기본값은 `Executors.newVirtualThreadPerTaskExecutor()`.
 */
class VirtualThreadAlgorithmAdapter(
    private val delegate: GraphAlgorithmRepository,
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
): GraphVirtualThreadAlgorithmRepository {

    companion object: KLogging()

    override fun pageRankAsync(options: PageRankOptions): CompletableFuture<List<PageRankScore>> =
        CompletableFuture.supplyAsync({ delegate.pageRank(options) }, executor)

    override fun degreeCentralityAsync(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): CompletableFuture<DegreeResult> =
        CompletableFuture.supplyAsync({ delegate.degreeCentrality(vertexId, options) }, executor)

    override fun connectedComponentsAsync(options: ComponentOptions): CompletableFuture<List<GraphComponent>> =
        CompletableFuture.supplyAsync({ delegate.connectedComponents(options) }, executor)

    override fun bfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions,
    ): CompletableFuture<List<TraversalVisit>> =
        CompletableFuture.supplyAsync({ delegate.bfs(startId, options) }, executor)

    override fun dfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions,
    ): CompletableFuture<List<TraversalVisit>> =
        CompletableFuture.supplyAsync({ delegate.dfs(startId, options) }, executor)

    override fun detectCyclesAsync(options: CycleOptions): CompletableFuture<List<GraphCycle>> =
        CompletableFuture.supplyAsync({ delegate.detectCycles(options) }, executor)
}

/**
 * [GraphAlgorithmRepository] 를 Virtual Thread 어댑터로 감싸는 확장 함수.
 *
 * ```kotlin
 * val vtOps = ops.asVirtualThread()
 * val future = vtOps.pageRankAsync()
 * ```
 *
 * @param executor 사용할 Virtual Thread executor.
 */
fun GraphAlgorithmRepository.asVirtualThread(
    executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
): GraphVirtualThreadAlgorithmRepository = VirtualThreadAlgorithmAdapter(this, executor)

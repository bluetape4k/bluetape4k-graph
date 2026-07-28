package io.bluetape4k.graph.repository

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
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread API용 graph analytics algorithm repository.
 *
 * blocking [GraphAlgorithmRepository]를 Java 25 Project Loom Virtual Thread에서 실행하고
 * 결과를 `CompletableFuture<T>`로 반환한다. 이 API는 Java code와
 * CompletableFuture 기반 pipeline을 대상으로 한다.
 *
 * Kotlin code는 [GraphSuspendAlgorithmRepository]를 우선 사용한다.
 *
 * 결과 ordering contract는 [GraphAlgorithmRepository]와 동일하다.
 *
 * ### Usage
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = ops.asVirtualThread()
 * val future = vtOps.pageRankAsync()
 * val scores = future.join()
 * ```
 */
interface GraphVirtualThreadAlgorithmRepository {

    fun pageRankAsync(options: PageRankOptions = PageRankOptions.Default): CompletableFuture<List<PageRankScore>>

    fun degreeCentralityAsync(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): CompletableFuture<DegreeResult>

    fun connectedComponentsAsync(
        options: ComponentOptions = ComponentOptions.Default,
    ): CompletableFuture<List<GraphComponent>>

    fun bfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): CompletableFuture<List<TraversalVisit>>

    fun dfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): CompletableFuture<List<TraversalVisit>>

    fun detectCyclesAsync(
        options: CycleOptions = CycleOptions.Default,
    ): CompletableFuture<List<GraphCycle>>
}

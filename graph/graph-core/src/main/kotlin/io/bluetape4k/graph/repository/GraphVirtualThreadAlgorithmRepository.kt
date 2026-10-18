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
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.graph.model.toTreeTraversalOptions
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

    @Deprecated(
        message = "Use bfsAsync(startId, TreeTraversalOptions(...))",
        ReplaceWith("bfsAsync(startId, options.toTreeTraversalOptions())"),
    )
    fun bfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): CompletableFuture<List<TraversalVisit>> = bfsAsync(startId, options.toTreeTraversalOptions())

    /** Runs BFS traversal with the replacement [TreeTraversalOptions] contract. */
    @Suppress("DEPRECATION")
    fun bfsAsync(
        startId: GraphElementId,
        options: TreeTraversalOptions,
    ): CompletableFuture<List<TraversalVisit>> = bfsAsync(
        startId,
        BfsDfsOptions(
            edgeLabel = options.edgeLabel,
            direction = options.direction,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
        ),
    )

    @Deprecated(
        message = "Use dfsAsync(startId, TreeTraversalOptions(...))",
        ReplaceWith("dfsAsync(startId, options.toTreeTraversalOptions())"),
    )
    fun dfsAsync(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): CompletableFuture<List<TraversalVisit>> = dfsAsync(startId, options.toTreeTraversalOptions())

    /** Runs DFS traversal with the replacement [TreeTraversalOptions] contract. */
    @Suppress("DEPRECATION")
    fun dfsAsync(
        startId: GraphElementId,
        options: TreeTraversalOptions,
    ): CompletableFuture<List<TraversalVisit>> = dfsAsync(
        startId,
        BfsDfsOptions(
            edgeLabel = options.edgeLabel,
            direction = options.direction,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
        ),
    )

    fun detectCyclesAsync(
        options: CycleOptions = CycleOptions.Default,
    ): CompletableFuture<List<GraphCycle>>
}

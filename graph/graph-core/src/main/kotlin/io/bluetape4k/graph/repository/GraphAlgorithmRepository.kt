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

/**
 * Graph analytics algorithm repository for the blocking API.
 *
 * Result ordering contract:
 * - [pageRank]: sorted by descending score.
 * - [connectedComponents]: ascending componentId; vertices inside each component are unordered.
 * - [bfs]: BFS visit order, level by level.
 * - [dfs]: DFS visit order, depth first.
 * - [detectCycles]: arbitrary order.
 *
 * Backend implementations may throw `UnsupportedOperationException` for unsupported algorithms.
 *
 * ### Usage
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val top10 = ops.pageRank(PageRankOptions(topK = 10))
 * val components = ops.connectedComponents()
 * val visits = ops.bfs(start.id, TreeTraversalOptions(maxDepth = 3, maxVertices = 1_000))
 * ```
 *
 * @see GraphSuspendAlgorithmRepository coroutine API
 */
interface GraphAlgorithmRepository {

    /**
     * Runs PageRank and returns the score list.
     *
     * Results are sorted by descending score.
     *
     * @param options PageRank options.
     * @return list of [PageRankScore] values.
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): List<PageRankScore>

    /**
     * 단일 vertex의 degree centrality를 계산한다.
     *
     * @param vertexId target vertex ID.
     * @param options degree options.
     * @return [DegreeResult].
     */
    fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * Detects connected components.
     *
     * @param options component options.
     * @return list of [GraphComponent] values, sorted by ascending componentId.
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): List<GraphComponent>

    /**
     * Runs BFS traversal.
     *
     * @param startId start vertex ID.
     * @param options BFS options.
     * @return list of [TraversalVisit] values in visit order.
     */
    @Deprecated(
        message = "Use bfs(startId, TreeTraversalOptions(...))",
        ReplaceWith("bfs(startId, options.toTreeTraversalOptions())"),
    )
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit> = bfs(startId, options.toTreeTraversalOptions())

    /** Runs BFS traversal with the replacement [TreeTraversalOptions] contract. */
    @Suppress("DEPRECATION")
    fun bfs(
        startId: GraphElementId,
        options: TreeTraversalOptions,
    ): List<TraversalVisit> = bfs(
        startId,
        BfsDfsOptions(
            edgeLabel = options.edgeLabel,
            direction = options.direction,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
        ),
    )

    /**
     * Runs DFS traversal.
     *
     * @param startId start vertex ID.
     * @param options DFS options.
     * @return list of [TraversalVisit] values in visit order.
     */
    @Deprecated(
        message = "Use dfs(startId, TreeTraversalOptions(...))",
        ReplaceWith("dfs(startId, options.toTreeTraversalOptions())"),
    )
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit> = dfs(startId, options.toTreeTraversalOptions())

    /** Runs DFS traversal with the replacement [TreeTraversalOptions] contract. */
    @Suppress("DEPRECATION")
    fun dfs(
        startId: GraphElementId,
        options: TreeTraversalOptions,
    ): List<TraversalVisit> = dfs(
        startId,
        BfsDfsOptions(
            edgeLabel = options.edgeLabel,
            direction = options.direction,
            maxDepth = options.maxDepth,
            maxVertices = options.maxVertices,
        ),
    )

    /**
     * Detects cycles.
     *
     * @param options cycle options.
     * @return list of [GraphCycle] values.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): List<GraphCycle>
}

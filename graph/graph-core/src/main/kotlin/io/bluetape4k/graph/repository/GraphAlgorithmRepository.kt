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
 * val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3))
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
     * Computes degree centrality for one vertex.
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
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    /**
     * Runs DFS traversal.
     *
     * @param startId start vertex ID.
     * @param options DFS options.
     * @return list of [TraversalVisit] values in visit order.
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

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

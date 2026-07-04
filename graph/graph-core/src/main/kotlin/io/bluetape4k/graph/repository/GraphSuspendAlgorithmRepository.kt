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
import kotlinx.coroutines.flow.Flow

/**
 * Graph analytics algorithm repository for the coroutine and Flow API.
 *
 * Flow ordering matches [GraphAlgorithmRepository].
 * The [pageRank] Flow emits scores in descending order.
 *
 * ### Usage
 * ```kotlin
 * runBlocking {
 *     val top10 = ops.pageRank(PageRankOptions(topK = 10)).toList()
 *     val components = ops.connectedComponents().toList()
 *     val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3)).toList()
 * }
 * ```
 *
 * @see GraphAlgorithmRepository blocking API
 */
interface GraphSuspendAlgorithmRepository {

    /**
     * Emits PageRank scores as a Flow in descending score order.
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): Flow<PageRankScore>

    /**
     * Computes degree centrality for one vertex.
     */
    suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * Emits connected components as a Flow.
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): Flow<GraphComponent>

    /**
     * Emits BFS visit events as a Flow.
     */
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * Emits DFS visit events as a Flow.
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * Emits detected cycles as a Flow.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): Flow<GraphCycle>
}

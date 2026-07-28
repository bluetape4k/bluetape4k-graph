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
 * coroutine 및 Flow API용 graph analytics algorithm repository.
 *
 * Flow ordering은 [GraphAlgorithmRepository]와 동일하다.
 * [pageRank] Flow는 score 내림차순으로 emit한다.
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
     * PageRank score를 score 내림차순 Flow로 emit한다.
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): Flow<PageRankScore>

    /**
     * 단일 vertex의 degree centrality를 계산한다.
     */
    suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * connected component를 Flow로 emit한다.
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): Flow<GraphComponent>

    /**
     * BFS visit event를 Flow로 emit한다.
     */
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * DFS visit event를 Flow로 emit한다.
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * 탐지된 cycle을 Flow로 emit한다.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): Flow<GraphCycle>
}

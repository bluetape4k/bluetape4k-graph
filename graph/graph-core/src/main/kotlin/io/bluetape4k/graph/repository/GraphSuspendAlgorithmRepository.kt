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
 * 그래프 분석(Analytics) 알고리즘 저장소 (코루틴/Flow 방식).
 *
 * Flow 순서 계약은 [GraphAlgorithmRepository] 와 동일하다.
 * [pageRank] Flow 는 score 내림차순으로 emit 된다.
 *
 * ### 사용 예제
 * ```kotlin
 * runBlocking {
 *     val top10 = ops.pageRank(PageRankOptions(topK = 10)).toList()
 *     val components = ops.connectedComponents().toList()
 *     val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3)).toList()
 * }
 * ```
 *
 * @see GraphAlgorithmRepository 동기 방식
 */
interface GraphSuspendAlgorithmRepository {

    /**
     * PageRank 점수를 Flow 로 emit 한다 (score 내림차순).
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): Flow<PageRankScore>

    /**
     * 단일 정점의 Degree Centrality 를 계산한다.
     */
    suspend fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * 연결 컴포넌트를 Flow 로 emit 한다.
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): Flow<GraphComponent>

    /**
     * BFS 방문 이벤트를 Flow 로 emit 한다.
     */
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * DFS 방문 이벤트를 Flow 로 emit 한다.
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): Flow<TraversalVisit>

    /**
     * 탐지된 순환을 Flow 로 emit 한다.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): Flow<GraphCycle>
}

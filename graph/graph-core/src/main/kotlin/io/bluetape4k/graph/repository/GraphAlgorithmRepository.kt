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
 * 그래프 분석(Analytics) 알고리즘 저장소 (동기 방식).
 *
 * 결과 순서 계약:
 * - [pageRank]: score 내림차순 정렬.
 * - [connectedComponents]: componentId 오름차순. 내부 정점은 임의 순서.
 * - [bfs]: BFS 방문 순서 (레벨 순).
 * - [dfs]: DFS 방문 순서 (깊이 우선).
 * - [detectCycles]: 임의 순서.
 *
 * 백엔드 미지원 알고리즘은 `UnsupportedOperationException` 을 던질 수 있다.
 *
 * ### 사용 예제
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val top10 = ops.pageRank(PageRankOptions(topK = 10))
 * val components = ops.connectedComponents()
 * val visits = ops.bfs(start.id, BfsDfsOptions(maxDepth = 3))
 * ```
 *
 * @see GraphSuspendAlgorithmRepository 코루틴 방식
 */
interface GraphAlgorithmRepository {

    /**
     * PageRank 알고리즘을 실행해 점수 목록을 반환한다.
     *
     * 반환 결과는 score 내림차순으로 정렬된다.
     *
     * @param options PageRank 옵션.
     * @return [PageRankScore] 목록.
     */
    fun pageRank(options: PageRankOptions = PageRankOptions.Default): List<PageRankScore>

    /**
     * 단일 정점의 Degree Centrality 를 계산한다.
     *
     * @param vertexId 측정 대상 정점 ID.
     * @param options Degree 옵션.
     * @return [DegreeResult].
     */
    fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions = DegreeOptions.Default,
    ): DegreeResult

    /**
     * 연결 컴포넌트를 탐지한다.
     *
     * @param options Component 옵션.
     * @return [GraphComponent] 목록 (componentId 오름차순).
     */
    fun connectedComponents(
        options: ComponentOptions = ComponentOptions.Default,
    ): List<GraphComponent>

    /**
     * BFS 탐색을 실행한다.
     *
     * @param startId 시작 정점 ID.
     * @param options BFS 옵션.
     * @return [TraversalVisit] 목록 (방문 순서).
     */
    fun bfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    /**
     * DFS 탐색을 실행한다.
     *
     * @param startId 시작 정점 ID.
     * @param options DFS 옵션.
     * @return [TraversalVisit] 목록 (방문 순서).
     */
    fun dfs(
        startId: GraphElementId,
        options: BfsDfsOptions = BfsDfsOptions.Default,
    ): List<TraversalVisit>

    /**
     * 순환을 탐지한다.
     *
     * @param options Cycle 옵션.
     * @return [GraphCycle] 목록.
     */
    fun detectCycles(
        options: CycleOptions = CycleOptions.Default,
    ): List<GraphCycle>
}

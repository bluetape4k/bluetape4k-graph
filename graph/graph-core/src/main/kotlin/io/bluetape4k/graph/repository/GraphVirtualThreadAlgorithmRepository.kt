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
 * Virtual Thread 기반 그래프 분석 알고리즘 저장소.
 *
 * Java 25 Project Loom 의 Virtual Thread 위에서 동기 [GraphAlgorithmRepository] 를 실행해
 * `CompletableFuture<T>` 로 결과를 반환한다. Java 코드 또는 CompletableFuture 기반 파이프라인과의
 * 상호운용을 위해 제공된다.
 *
 * Kotlin 코드에서는 [GraphSuspendAlgorithmRepository] 사용을 권장한다.
 *
 * 결과 순서 계약: [GraphAlgorithmRepository] 와 동일.
 *
 * ### 사용 예제
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

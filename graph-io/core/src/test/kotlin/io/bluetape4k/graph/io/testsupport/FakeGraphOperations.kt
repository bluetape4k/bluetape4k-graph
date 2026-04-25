package io.bluetape4k.graph.io.testsupport

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphOperations

/**
 * 테스트 전용 [GraphOperations] stub.
 *
 * 어댑터가 오직 `GraphOperations` 타입만 요구하고 실제 호출은 일어나지 않는 단위 테스트에서 사용한다.
 * 모든 메서드는 호출되면 명시적으로 실패하도록 `error(...)` stub으로 구현된다.
 */
class FakeGraphOperations: GraphOperations {

    // --- GraphSession / AutoCloseable ---
    override fun createGraph(name: String): Unit = error("not used in this test")
    override fun dropGraph(name: String): Unit = error("not used in this test")
    override fun graphExists(name: String): Boolean = error("not used in this test")
    override fun close(): Unit = error("not used in this test")

    // --- GraphVertexRepository ---
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        error("not used in this test")

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? =
        error("not used in this test")

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
        error("not used in this test")

    override fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? = error("not used in this test")

    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        error("not used in this test")

    override fun countVertices(label: String): Long = error("not used in this test")

    // --- GraphEdgeRepository ---
    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge = error("not used in this test")

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
        error("not used in this test")

    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        error("not used in this test")

    // --- GraphTraversalRepository ---
    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> =
        error("not used in this test")

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? = error("not used in this test")

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> = error("not used in this test")

    // --- GraphAlgorithmRepository ---
    override fun pageRank(options: PageRankOptions): List<PageRankScore> =
        error("not used in this test")

    override fun degreeCentrality(
        vertexId: GraphElementId,
        options: DegreeOptions,
    ): DegreeResult = error("not used in this test")

    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> =
        error("not used in this test")

    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> =
        error("not used in this test")

    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> =
        error("not used in this test")

    override fun detectCycles(options: CycleOptions): List<GraphCycle> =
        error("not used in this test")
}

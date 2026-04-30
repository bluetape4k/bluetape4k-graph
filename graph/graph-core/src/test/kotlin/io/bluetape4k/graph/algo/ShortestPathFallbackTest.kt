package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.Direction
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
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeNear
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test

/**
 * 테스트 그래프:
 *
 * A --(1.0)--> B --(2.0)--> C     X (고립)
 *              |
 *           (1.0, self-loop)
 *
 * OUTGOING 최단 경로 A→C: A→B→C (cost=3.0)
 * INCOMING 최단 경로 C→A: C←B←A (cost=3.0)
 * BOTH     최단 경로 A→C: A→B→C (cost=3.0)
 */
class ShortestPathFallbackTest {

    companion object : KLogging()

    private val eAB = edge("eAB", "A", "B", 1.0)
    private val eBC = edge("eBC", "B", "C", 2.0)
    private val eBB = edge("eBB", "B", "B", 1.0)  // self-loop

    private val vertices: Map<String, GraphVertex> = mapOf(
        "A" to vertex("A"),
        "B" to vertex("B"),
        "C" to vertex("C"),
        "X" to vertex("X"),
    )

    private fun outgoingOps() = FakeGraphOperations(
        vertices = vertices,
        edgesByStart = mapOf("A" to listOf(eAB), "B" to listOf(eBC)),
        edgesByEnd = emptyMap(),
    )

    private fun incomingOps() = FakeGraphOperations(
        vertices = vertices,
        edgesByStart = emptyMap(),
        edgesByEnd = mapOf("B" to listOf(eAB), "C" to listOf(eBC)),
    )

    private fun bothOps() = FakeGraphOperations(
        vertices = vertices,
        edgesByStart = mapOf("A" to listOf(eAB), "B" to listOf(eBC)),
        edgesByEnd = mapOf("B" to listOf(eAB), "C" to listOf(eBC)),
    )

    private fun bothWithSelfLoopOps() = FakeGraphOperations(
        vertices = vertices,
        edgesByStart = mapOf("A" to listOf(eAB), "B" to listOf(eBC, eBB)),
        edgesByEnd = mapOf("B" to listOf(eAB, eBB), "C" to listOf(eBC)),
    )

    private fun options(direction: Direction = Direction.OUTGOING) =
        PathOptions(weightProperty = "weight", direction = direction)

    // ─── Dijkstra ────────────────────────────────────────────────────────────

    @Test
    fun `dijkstra OUTGOING 방향에서 A에서 C까지 최단 경로를 찾는다`() {
        val path = ShortestPathFallback.dijkstra(outgoingOps(), id("A"), id("C"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `dijkstra INCOMING 방향에서 C에서 A까지 역방향 최단 경로를 찾는다`() {
        val path = ShortestPathFallback.dijkstra(
            incomingOps(), id("C"), id("A"), options(Direction.INCOMING)
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `dijkstra BOTH 방향에서 A에서 C까지 양방향 최단 경로를 찾는다`() {
        val path = ShortestPathFallback.dijkstra(
            bothOps(), id("A"), id("C"), options(Direction.BOTH)
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `dijkstra BOTH 방향에서 self-loop가 있어도 올바른 최단 경로를 반환한다`() {
        val path = ShortestPathFallback.dijkstra(
            bothWithSelfLoopOps(), id("A"), id("C"), options(Direction.BOTH)
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
        path.edges shouldHaveSize 2
    }

    @Test
    fun `dijkstra 연결되지 않은 정점 간 탐색 시 null을 반환한다`() {
        ShortestPathFallback.dijkstra(outgoingOps(), id("A"), id("X"), options()).shouldBeNull()
    }

    // ─── A* ──────────────────────────────────────────────────────────────────

    private val zeroHeuristic: (GraphVertex) -> Double = { 0.0 }

    @Test
    fun `aStar OUTGOING 방향에서 A에서 C까지 최단 경로를 찾는다`() {
        val path = ShortestPathFallback.aStar(
            outgoingOps(), id("A"), id("C"), options(), zeroHeuristic
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `aStar INCOMING 방향에서 C에서 A까지 역방향 최단 경로를 찾는다`() {
        val path = ShortestPathFallback.aStar(
            incomingOps(), id("C"), id("A"), options(Direction.INCOMING), zeroHeuristic
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `aStar BOTH 방향에서 self-loop가 있어도 올바른 최단 경로를 반환한다`() {
        val path = ShortestPathFallback.aStar(
            bothWithSelfLoopOps(), id("A"), id("C"), options(Direction.BOTH), zeroHeuristic
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
        path.edges shouldHaveSize 2
    }

    @Test
    fun `aStar 연결되지 않은 정점 간 탐색 시 null을 반환한다`() {
        ShortestPathFallback.aStar(outgoingOps(), id("A"), id("X"), options(), zeroHeuristic).shouldBeNull()
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun id(v: String) = GraphElementId.of(v)
    private fun vertex(id: String) = GraphVertex(GraphElementId.of(id), "V", emptyMap())
    private fun edge(id: String, from: String, to: String, weight: Double) = GraphEdge(
        GraphElementId.of(id),
        "ROAD",
        GraphElementId.of(from),
        GraphElementId.of(to),
        mapOf("weight" to weight),
    )

    private fun GraphPath.vertexIds(): List<String> =
        steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex.id.value }
}

/**
 * ShortestPathFallback이 실제로 호출하는 3개 메서드만 구현하는 테스트 스텁.
 * 나머지 메서드는 호출 시 즉시 실패해 불필요한 호출을 감지한다.
 */
private class FakeGraphOperations(
    private val vertices: Map<String, GraphVertex>,
    private val edgesByStart: Map<String, List<GraphEdge>>,
    private val edgesByEnd: Map<String, List<GraphEdge>>,
) : GraphOperations {

    override fun findVertexById(id: GraphElementId): GraphVertex? = vertices[id.value]
    override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> =
        edgesByStart[startId.value] ?: emptyList()
    override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> =
        edgesByEnd[endId.value] ?: emptyList()

    override fun close(): Unit = Unit
    override fun createGraph(name: String): Unit = error("not used")
    override fun dropGraph(name: String): Unit = error("not used")
    override fun graphExists(name: String): Boolean = error("not used")
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex = error("not used")
    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? = error("not used")
    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> = error("not used")
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? = error("not used")
    override fun deleteVertex(label: String, id: GraphElementId): Boolean = error("not used")
    override fun countVertices(label: String): Long = error("not used")
    override fun createEdge(fromId: GraphElementId, toId: GraphElementId, label: String, properties: Map<String, Any?>): GraphEdge = error("not used")
    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> = error("not used")
    override fun deleteEdge(label: String, id: GraphElementId): Boolean = error("not used")
    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> = error("not used")
    override fun shortestPath(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): GraphPath? = error("not used")
    override fun allPaths(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): List<GraphPath> = error("not used")
    override fun aStarPath(fromId: GraphElementId, toId: GraphElementId, options: PathOptions, heuristic: (GraphVertex) -> Double): GraphPath? = error("not used")
    override fun pageRank(options: PageRankOptions): List<PageRankScore> = error("not used")
    override fun degreeCentrality(vertexId: GraphElementId, options: DegreeOptions): DegreeResult = error("not used")
    override fun connectedComponents(options: ComponentOptions): List<GraphComponent> = error("not used")
    override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> = error("not used")
    override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> = error("not used")
    override fun detectCycles(options: CycleOptions): List<GraphCycle> = error("not used")
}

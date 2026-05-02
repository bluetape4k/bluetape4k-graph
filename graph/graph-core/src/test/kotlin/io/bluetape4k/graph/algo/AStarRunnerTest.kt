package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeNear
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * 테스트 그래프 (격자 구조):
 *
 * A(0,0) --(1.0)--> B(1,0) --(1.0)--> C(2,0)
 * |                                     ^
 * +-------------(3.0)-------------------+
 *
 * A→C 최단 경로: A→B→C (cost=2.0)
 */
class AStarRunnerTest {

    companion object : KLogging()

    data class Coord(val x: Double, val y: Double)

    private val coords = mapOf(
        "A" to Coord(0.0, 0.0),
        "B" to Coord(1.0, 0.0),
        "C" to Coord(2.0, 0.0),
    )

    private fun euclidean(v: GraphVertex, goalId: String): Double {
        val vc = coords[v.id.value] ?: return 0.0
        val gc = coords[goalId] ?: return 0.0
        val dx = vc.x - gc.x
        val dy = vc.y - gc.y
        return sqrt(dx * dx + dy * dy)
    }

    private val eAB = edge("eAB", "A", "B", 1.0)
    private val eBC = edge("eBC", "B", "C", 1.0)
    private val eAC = edge("eAC", "A", "C", 3.0)

    private val mainGraph = mapOf(
        "A" to listOf(eAB, eAC),
        "B" to listOf(eBC),
        "C" to emptyList<GraphEdge>(),
    )

    private val vertices: Map<String, GraphVertex> = mapOf(
        "A" to vertex("A"), "B" to vertex("B"), "C" to vertex("C"),
    )

    private fun runner(
        edgeMap: Map<String, List<GraphEdge>> = mainGraph,
        goalId: String = "C",
    ) = AStarRunner(
        fetchEdges = { id -> edgeMap[id.value] ?: emptyList() },
        fetchVertex = { id -> vertices[id.value] },
        heuristic = { v -> euclidean(v, goalId) },
    )

    private fun options(weight: String = "cost") = PathOptions(weightProperty = weight)

    // ─── 기본 경로 탐색 ─────────────────────────────────────────────────────────

    @Test
    fun `A에서 C까지 최단 경로는 A-B-C이다`() {
        val path = runner().run(id("A"), id("C"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(2.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `A에서 B까지 최단 경로는 A-B이다`() {
        val bRunner = AStarRunner(
            fetchEdges = { id -> mainGraph[id.value] ?: emptyList() },
            fetchVertex = { id -> vertices[id.value] },
            heuristic = { v -> euclidean(v, "B") },
        )
        val path = bRunner.run(id("A"), id("B"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(1.0, 0.001)
    }

    @Test
    fun `경로가 없으면 null 반환`() {
        runner().run(id("C"), id("A"), options()).shouldBeNull()
    }

    @Test
    fun `출발지와 도착지가 같으면 단일 정점 경로 반환`() {
        val path = runner().run(id("A"), id("A"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(0.0, 0.001)
        path.steps shouldHaveSize 1
        path.steps.filterIsInstance<PathStep.VertexStep>() shouldHaveSize 1
    }

    @Test
    fun `출발 정점이 없으면 null 반환`() {
        runner().run(id("MISSING"), id("C"), options()).shouldBeNull()
    }

    // ─── 휴리스틱 = 0 (Dijkstra 동치) ────────────────────────────────────────────

    @Test
    fun `영 휴리스틱은 Dijkstra와 동일한 결과를 반환한다`() {
        val zeroH = AStarRunner(
            fetchEdges = { id -> mainGraph[id.value] ?: emptyList() },
            fetchVertex = { id -> vertices[id.value] },
            heuristic = { _ -> 0.0 },
        )
        val path = zeroH.run(id("A"), id("C"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(2.0, 0.001)
    }

    // ─── maxVisited 제한 ─────────────────────────────────────────────────────────

    @Test
    fun `maxVisited 초과 시 null 반환`() {
        val opts = PathOptions(weightProperty = "cost", maxVisited = 1)
        runner().run(id("A"), id("C"), opts).shouldBeNull()
    }

    // ─── MissingWeightPolicy ─────────────────────────────────────────────────────

    @Test
    fun `Skip 정책에서 weight 없는 간선은 건너뜀`() {
        val skipGraph = mapOf(
            "A" to listOf(edge("eAB_nw", "A", "B", null)),
            "B" to listOf(eBC),
            "C" to emptyList<GraphEdge>(),
        )
        val opts = PathOptions(weightProperty = "cost", missingWeightPolicy = MissingWeightPolicy.Skip)
        runner(skipGraph).run(id("A"), id("C"), opts).shouldBeNull()
    }

    @Test
    fun `UseDefault 정책에서 weight 없는 간선은 기본값 사용`() {
        val defaultGraph = mapOf(
            "A" to listOf(edge("eAB_nw", "A", "B", null)),
            "B" to listOf(eBC),
            "C" to emptyList<GraphEdge>(),
        )
        val opts = PathOptions(
            weightProperty = "cost",
            missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
        )
        val path = runner(defaultGraph).run(id("A"), id("C"), opts).shouldNotBeNull()

        path.totalWeight.shouldBeNear(2.0, 0.001)
    }

    // ─── weightProperty 필수 ──────────────────────────────────────────────────���──

    @Test
    fun `weightProperty 없으면 IllegalArgumentException 발생`() {
        val block: () -> Unit = { runner().run(id("A"), id("C"), PathOptions()) }
        block shouldThrow IllegalArgumentException::class
    }

    // ─── Direction.BOTH ───────────────────────────────────────────────────────────

    @Test
    fun `Direction BOTH에서 역방향 경로 탐색`() {
        // C→A 방향으로 탐색: 단방향 그래프이므로 역방향 간선을 제공
        val bothRunner = AStarRunner(
            fetchEdges = { id ->
                val outgoing = mainGraph[id.value] ?: emptyList()
                val incoming = mainGraph.values.flatten().filter { it.endId == id }
                (outgoing + incoming).distinctBy { it.id }
            },
            fetchVertex = { vid -> vertices[vid.value] },
            heuristic = { v -> euclidean(v, "A") },
        )
        val opts = PathOptions(weightProperty = "cost", direction = Direction.BOTH)
        // C에서 A로 역방향: BOTH 모드에서는 B→A 역방향 간선을 활용
        val path = bothRunner.run(id("C"), id("A"), opts).shouldNotBeNull()

        path.totalWeight.shouldBeNear(2.0, 0.001)
    }

    // ─── null neighbor mid-traversal ───────────────────────────────────────────

    @Test
    fun `이웃 정점이 없으면 해당 이웃을 건너뛰고 대체 경로 탐색`() {
        // B를 fetchVertex에서 제외 → A→B 경로 사용 불가, A→C(직선)만 사용
        val partialVertices = mapOf("A" to vertex("A"), "C" to vertex("C"))
        val partialRunner = AStarRunner(
            fetchEdges = { vid -> mainGraph[vid.value] ?: emptyList() },
            fetchVertex = { vid -> partialVertices[vid.value] },
            heuristic = { v -> euclidean(v, "C") },
        )
        val path = partialRunner.run(id("A"), id("C"), options()).shouldNotBeNull()

        // A→B 경로에서 B 정점을 찾지 못하므로 A→C 직선 경로(cost=3.0)만 유효
        path.totalWeight.shouldBeNear(3.0, 0.001)
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────────

    private fun id(v: String) = GraphElementId.of(v)

    private fun vertex(id: String) = GraphVertex(GraphElementId.of(id), "V", emptyMap())

    private fun edge(id: String, from: String, to: String, weight: Double?) = GraphEdge(
        GraphElementId.of(id),
        "ROAD",
        GraphElementId.of(from),
        GraphElementId.of(to),
        if (weight != null) mapOf("cost" to weight) else emptyMap(),
    )

    private fun io.bluetape4k.graph.model.GraphPath.vertexIds(): List<String> =
        steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex.id.value }
}

package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import io.bluetape4k.assertions.shouldBeNear
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContainAll
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test

/**
 * 테스트 그래프:
 *
 * A --(1.0)--> B --(2.0)--> C
 * |                          ^
 * +--------(5.0)-------------+
 *
 * 최단 경로 A→C: A→B→C (cost=3.0)
 * 최단 경로 A→B: A→B (cost=1.0)
 */
class DijkstraRunnerTest {

    companion object : KLogging()

    private val eAB = edge("eAB", "A", "B", 1.0)
    private val eBC = edge("eBC", "B", "C", 2.0)
    private val eAC = edge("eAC", "A", "C", 5.0)
    private val eXY = edge("eXY", "X", "Y", 3.0)
    private val eYZ = edge("eYZ", "Y", "Z", 4.0)

    private val mainGraph: Map<String, List<GraphEdge>> = mapOf(
        "A" to listOf(eAB, eAC),
        "B" to listOf(eBC),
        "C" to emptyList(),
    )

    private val linearGraph: Map<String, List<GraphEdge>> = mapOf(
        "X" to listOf(eXY),
        "Y" to listOf(eYZ),
        "Z" to emptyList(),
    )

    private val vertices: Map<String, GraphVertex> = mapOf(
        "A" to vertex("A"), "B" to vertex("B"), "C" to vertex("C"),
        "X" to vertex("X"), "Y" to vertex("Y"), "Z" to vertex("Z"),
    )

    private fun runner(edgeMap: Map<String, List<GraphEdge>> = mainGraph) = DijkstraRunner(
        fetchEdges = { id -> edgeMap[id.value] ?: emptyList() },
        fetchVertex = { id -> vertices[id.value] },
    )

    private fun options(weight: String = "cost") = PathOptions(weightProperty = weight)

    // ─── 기본 경로 탐색 ─────────────────────────────────────────────────────────

    @Test
    fun `A에서 C까지 최단 경로는 A-B-C이다`() {
        val path = runner().run(id("A"), id("C"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `A에서 B까지 최단 경로는 A-B이다`() {
        val path = runner().run(id("A"), id("B"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(1.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B")
    }

    @Test
    fun `직접 연결이 우회 경로보다 비싸면 우회 경로 선택`() {
        val cheapDetour = mapOf(
            "A" to listOf(edge("eAC_exp", "A", "C", 10.0), edge("eAB2", "A", "B", 1.0)),
            "B" to listOf(edge("eBC2", "B", "C", 1.0)),
            "C" to emptyList<GraphEdge>(),
        )
        val path = runner(cheapDetour).run(id("A"), id("C"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(2.0, 0.001)
        path.vertexIds() shouldContainAll listOf("A", "B", "C")
    }

    @Test
    fun `동일 출발지와 도착지는 단일 정점 경로 반환`() {
        val path = runner().run(id("A"), id("A"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(0.0, 0.001)
        // 출발 정점만 있고 간선 없음
        path.steps shouldHaveSize 1
        path.steps.filterIsInstance<PathStep.VertexStep>() shouldHaveSize 1
    }

    @Test
    fun `경로가 없으면 null 반환`() {
        runner().run(id("C"), id("A"), options()).shouldBeNull()
    }

    @Test
    fun `출발지 정점이 존재하지 않으면 null 반환`() {
        runner().run(id("UNKNOWN"), id("C"), options()).shouldBeNull()
    }

    // ─── Direction.BOTH ─────────────────────────────────────────────────────────

    @Test
    fun `Direction BOTH에서 역방향 경로 탐색`() {
        val bothRunner = DijkstraRunner(
            fetchEdges = { id ->
                val outgoing = linearGraph[id.value] ?: emptyList()
                val incoming = linearGraph.values.flatten().filter { it.endId == id }
                (outgoing + incoming).distinctBy { it.id }
            },
            fetchVertex = { id -> vertices[id.value] },
        )
        val opts = PathOptions(weightProperty = "cost", direction = Direction.BOTH)
        val path = bothRunner.run(id("Z"), id("X"), opts).shouldNotBeNull()

        path.totalWeight.shouldBeNear(7.0, 0.001)
    }

    // ─── maxVisited 제한 ─────────────────────────────────────────────────────────

    @Test
    fun `maxVisited 초과 시 null 반환`() {
        val opts = PathOptions(weightProperty = "cost", maxVisited = 1)
        runner().run(id("A"), id("C"), opts).shouldBeNull()
    }

    @Test
    fun `maxDepth보다 긴 경로는 제외하고 경계 안의 weighted path를 선택한다`() {
        val opts = PathOptions(weightProperty = "cost", maxDepth = 1)

        val path = runner().run(id("A"), id("C"), opts).shouldNotBeNull()

        path.vertexIds() shouldBeEqualTo listOf("A", "C")
        path.totalWeight.shouldBeNear(5.0, 0.001)
    }

    @Test
    fun `maxDepth 0은 source와 target이 같을 때만 vertex-only path를 허용한다`() {
        val opts = PathOptions(weightProperty = "cost", maxDepth = 0)

        runner().run(id("A"), id("A"), opts).shouldNotBeNull().length shouldBeEqualTo 0
        runner().run(id("A"), id("C"), opts).shouldBeNull()
    }

    @Test
    fun `더 싼 깊은 경로가 shallow 경로를 가리지 않고 maxDepth 내 후속 경로를 보존한다`() {
        val boundedGraph = mapOf(
            "A" to listOf(edge("eAX", "A", "X", 0.1), edge("eAB_shallow", "A", "B", 5.0)),
            "X" to listOf(edge("eXB", "X", "B", 0.1)),
            "B" to listOf(edge("eBC", "B", "C", 1.0)),
            "C" to emptyList<GraphEdge>(),
        )
        val opts = PathOptions(weightProperty = "cost", maxDepth = 2)

        val path = runner(boundedGraph).run(id("A"), id("C"), opts).shouldNotBeNull()

        path.vertexIds() shouldBeEqualTo listOf("A", "B", "C")
        path.totalWeight.shouldBeNear(6.0, 0.001)
    }

    // ─── MissingWeightPolicy 통합 ─────────────────────────────────────────────────

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

        path.totalWeight.shouldBeNear(3.0, 0.001)
    }

    // ─── weightProperty 필수 ─────────────────────────────────────────────────────

    @Test
    fun `weightProperty가 null이면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> { runner().run(id("A"), id("C"), PathOptions()) }
    }

    // ─── fetchVertex mid-traversal null ──────────────────────────────────────────

    @Test
    fun `탐색 중 정점을 찾을 수 없으면 해당 이웃을 건너뛰고 null을 반환한다`() {
        // A → B (fetchVertex("B") = null) → C: B를 건너뛰므로 A→C 경로 없음
        val eAB2 = edge("eAB2", "A", "B", 1.0)
        val eBC2 = edge("eBC2", "B", "C", 1.0)
        val nullVertexRunner = DijkstraRunner(
            fetchEdges = { id ->
                when (id.value) {
                    "A" -> listOf(eAB2)
                    "B" -> listOf(eBC2)
                    else -> emptyList()
                }
            },
            fetchVertex = { id -> if (id.value == "B") null else GraphVertex(id, "V", emptyMap()) },
        )
        nullVertexRunner.run(id("A"), id("C"), PathOptions(weightProperty = "cost")).shouldBeNull()
    }

    // ─── totalWeight 검증 ────────────────────────────────────────────────────────

    @Test
    fun `totalWeight는 경로상 모든 간선 가중치 합과 같다`() {
        val path = runner(linearGraph).run(id("X"), id("Z"), options()).shouldNotBeNull()

        path.totalWeight.shouldBeNear(7.0, 0.001)
    }

    // ─── 헬퍼 함수 ────────────────────────────────────────────────────────────────

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

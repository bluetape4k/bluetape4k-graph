package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.transaction
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FalkorDBGraphOperationsTest : AbstractFalkorDBTest() {

    private lateinit var ops: FalkorDBGraphOperations

    @BeforeAll
    override fun setupAll() {
        super.setupAll()
        ops = FalkorDBGraphOperations(driver, graphName)
    }

    @BeforeEach
    fun cleanup() {
        ops.dropGraph(graphName)
    }

    // ----- 그래프 초기화 -----

    @Test
    @Order(10)
    fun `createVertex creates vertex with id and label`() {
        val vertex = ops.createVertex("Person")
        vertex.id.value.shouldNotBeEmpty()
        vertex.label shouldBeEqualTo "Person"
    }

    @Test
    @Order(11)
    fun `graphExists returns true when graph has data`() {
        // dropGraph 후 첫 쿼리로 lazy 생성됨
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.graphExists(graphName).shouldBeTrue()
    }

    @Test
    @Order(12)
    fun `graphExists returns false for non-existent graph`() {
        val result = ops.graphExists("non_existent_graph_xyz_12345")
        // 존재하지 않으면 false
        result shouldBeEqualTo false
    }

    @Test
    @Order(13)
    fun `graphExists preserves driver failures`() {
        val failingDriver = mockk<com.falkordb.Driver>()
        every { failingDriver.listGraphs() } throws IllegalStateException("redis unavailable")
        val failingOps = FalkorDBGraphOperations(failingDriver, graphName)

        val ex = assertFailsWith<GraphQueryException> {
            failingOps.graphExists(graphName)
        }

        ex.message shouldContain "FalkorDB graphExists failed"
        ex.cause shouldBeInstanceOf IllegalStateException::class
    }

    // ----- 정점(Vertex) CRUD -----

    @Test
    @Order(20)
    fun `label과 properties로 정점을 생성한다`() {
        val props = mapOf("name" to "Alice", "age" to 30L)
        val vertex = ops.createVertex("Person", props)

        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(21)
    fun `findVertexById로 생성된 정점을 조회한다`() {
        val created = ops.createVertex("Person", mapOf("name" to "Bob"))
        val found = ops.findVertexById("Person", created.id)

        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(22)
    fun `존재하지 않는 id로 조회하면 null 반환`() {
        val fakeId = GraphElementId.of("999999999")
        val result = ops.findVertexById("Person", fakeId)
        result.shouldBeNull()
    }

    @Test
    @Order(23)
    fun `findVerticesByLabel로 label에 해당하는 정점 목록을 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("Car", mapOf("model" to "Tesla"))

        val persons = ops.findVerticesByLabel("Person")
        persons.shouldHaveSize(2)
        persons.all { it.label == "Person" }.shouldBeTrue()
    }

    @Test
    @Order(24)
    fun `filter 조건으로 정점을 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        ops.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))

        val result = ops.findVerticesByLabel("Person", mapOf("city" to "Seoul"))
        result.shouldHaveSize(1)
        result[0].properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    @Order(25)
    fun `updateVertex로 정점 properties를 업데이트한다`() {
        val vertex = ops.createVertex("Person", mapOf("name" to "Charlie", "age" to 25L))
        val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 26L))

        updated.shouldNotBeNull()
        updated.id shouldBeEqualTo vertex.id
        updated.properties["age"] shouldBeEqualTo 26L
    }

    @Test
    @Order(26)
    fun `deleteVertex로 정점을 삭제하면 재조회 시 null 반환`() {
        val vertex = ops.createVertex("Person", mapOf("name" to "Dave"))
        val deleted = ops.deleteVertex("Person", vertex.id)
        deleted.shouldBeTrue()

        ops.findVertexById("Person", vertex.id).shouldBeNull()
    }

    @Test
    @Order(27)
    fun `countVertices로 정점 개수를 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))

        val count = ops.countVertices("Person")
        count shouldBeEqualTo 2L
    }

    @Test
    @Order(28)
    fun `transaction은 중간 결과가 필요한 repository DSL을 지원하지 않는다`() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            ops.transaction {
                createVertex("Person")
            }
        }

        ex.message shouldContain "does not support graph transactions"
    }

    // ----- 간선(Edge) CRUD -----

    @Test
    @Order(30)
    fun `createEdge로 두 정점 사이에 간선을 생성한다`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))

        val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2020L))

        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo alice.id
        edge.endId shouldBeEqualTo bob.id
        edge.properties["since"] shouldBeEqualTo 2020L
    }

    @Test
    @Order(31)
    fun `findEdgesByLabel로 label에 해당하는 간선 목록을 조회한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")
        ops.createEdge(a.id, c.id, "FOLLOWS")

        val knowsEdges = ops.findEdgesByLabel("KNOWS")
        knowsEdges.shouldHaveSize(2)
        knowsEdges.all { it.label == "KNOWS" }.shouldBeTrue()
    }

    @Test
    @Order(32)
    fun `deleteEdge로 간선을 삭제하면 재조회 시 empty`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val edge = ops.createEdge(a.id, b.id, "KNOWS")

        val deleted = ops.deleteEdge("KNOWS", edge.id)
        deleted.shouldBeTrue()

        ops.findEdgesByLabel("KNOWS").shouldHaveSize(0)
    }

    // ----- 그래프 탐색 (Traversal) -----

    @Test
    @Order(40)
    fun `neighbors로 OUTGOING 이웃 정점을 조회한다`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))

        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(alice.id, carol.id, "KNOWS")

        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING))
        neighbors.shouldHaveSize(2)
        val names = neighbors.map { it.properties["name"] }
        names shouldContain "Bob"
        names shouldContain "Carol"
    }

    @Test
    @Order(41)
    fun `neighbors로 INCOMING 이웃 정점을 조회한다`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))

        ops.createEdge(bob.id, alice.id, "KNOWS")
        ops.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING))
        neighbors.shouldHaveSize(2)
        val names = neighbors.map { it.properties["name"] }
        names shouldContain "Bob"
        names shouldContain "Carol"
    }

    @Test
    @Order(42)
    fun `neighbors로 BOTH 방향 이웃 정점을 조회한다`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))

        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH))
        neighbors.shouldHaveSize(2)
        val names = neighbors.map { it.properties["name"] }
        names shouldContain "Bob"
        names shouldContain "Carol"
    }

    @Test
    @Order(43)
    fun `depth=2로 2단계 이웃 정점을 조회한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")

        val neighbors = ops.neighbors(
            a.id,
            NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
        )
        neighbors.shouldNotBeEmpty()
        val names = neighbors.map { it.properties["name"] }
        names shouldContain "B"
        names shouldContain "C"
    }

    @Test
    @Order(50)
    fun `shortestPath로 최단 경로를 탐색한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")

        val path = ops.shortestPath(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))
        path.shouldNotBeNull()
        path.vertices.shouldNotBeEmpty()
    }

    @Test
    @Order(51)
    fun `연결되지 않은 경우 shortestPath는 null 반환`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))

        // 간선 없음
        val path = ops.shortestPath(a.id, b.id, PathOptions(edgeLabel = "KNOWS"))
        path.shouldBeNull()
    }

    @Test
    @Order(52)
    fun `allPaths로 모든 경로를 탐색한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")
        ops.createEdge(a.id, c.id, "KNOWS")

        val paths = ops.allPaths(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))
        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterOrEqualTo 2
    }
}

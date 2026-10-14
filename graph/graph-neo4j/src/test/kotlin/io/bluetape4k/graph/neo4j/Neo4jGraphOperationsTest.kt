package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldContainSame
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.neo4j.driver.Driver
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.exceptions.ServiceUnavailableException

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Neo4jGraphOperationsTest: AbstractNeo4jTest() {

    companion object: KLogging()

    @BeforeEach
    override fun beforeEach() {
        ops.createGraph(graphName)
        ops.dropGraph(graphName)
    }

    // ----- 그래프 초기화 -----

    @Test
    @Order(10)
    fun `기본 current graph가 존재하면 true 반환`() {
        ops.graphExists("default").shouldBeTrue()
    }

    @Test
    @Order(11)
    fun `graphExists preserves driver failures`() {
        val failingDriver = mockk<Driver>()
        every {
            failingDriver.session(any<SessionConfig>())
        } throws ServiceUnavailableException("neo4j unavailable")

        val failingOps = Neo4jGraphOperations(failingDriver)

        val ex = assertFailsWith<GraphQueryException> {
            failingOps.graphExists("default")
        }

        ex.message shouldContain "Neo4j graphExists failed"
        ex.cause shouldBeInstanceOf ServiceUnavailableException::class
    }

    @Test
    @Order(13)
    fun `다른 graph name으로 dropGraph하면 현재 graph를 삭제하지 않는다`() {
        val currentGraph = "current"
        ops.createGraph(currentGraph)
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.graphExists(currentGraph).shouldBeTrue()
        ops.graphExists("other").shouldBeFalse()

        val ex = assertFailsWith<GraphQueryException> {
            ops.dropGraph("other")
        }

        ex.message shouldContain currentGraph
        ops.countVertices("Person") shouldBeEqualTo 1L

        ops.dropGraph(currentGraph)
        ops.countVertices("Person") shouldBeEqualTo 0L
    }

    @Test
    @Order(12)
    fun `dropGraph로 전체 데이터를 삭제한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.countVertices("Person") shouldBeGreaterOrEqualTo 1L

        ops.dropGraph(graphName)
        ops.countVertices("Person") shouldBeEqualTo 0L
    }

    // ----- 정점(Vertex) CRUD -----

    @Test
    @Order(20)
    fun `정점을 생성하면 elementId가 부여된다`() {
        val vertex = ops.createVertex("Person")

        log.debug { "vertx=$vertex" }
        vertex.id.value.shouldNotBeEmpty()
        vertex.label shouldBeEqualTo "Person"
    }

    @Test
    @Order(21)
    fun `label과 properties로 정점을 생성한다`() {
        val props = mapOf("name" to "Alice", "age" to 30L)
        val vertex = ops.createVertex("Person", props)

        log.debug { "vertx=$vertex" }
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(22)
    fun `id로 정점을 조회한다`() {
        val created = ops.createVertex("Person", mapOf("name" to "Bob"))
        val found = ops.findVertexById("Person", created.id)

        log.debug { "found=$found" }
        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(23)
    fun `존재하지 않는 id로 조회하면 null 반환`() {
        val fakeId = "4:00000000-0000-0000-0000-000000000000:9999".toGraphElementId()
        ops.findVertexById("Person", fakeId).shouldBeNull()
    }

    @Test
    @Order(24)
    fun `label로 정점 목록을 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("Car", mapOf("model" to "Tesla"))

        val persons = ops.findVerticesByLabel("Person")
            .onEach { log.debug { "person=$it" } }

        persons shouldHaveSize 2
        persons.all { it.label == "Person" }.shouldBeTrue()
    }

    @Test
    @Order(25)
    fun `filter 조건으로 정점을 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        ops.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))

        val result = ops.findVerticesByLabel("Person", mapOf("city" to "Seoul"))
            .onEach { log.debug { "person=$it" } }

        result shouldHaveSize 1
        result.single().properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    @Order(26)
    fun `정점의 properties를 업데이트한다`() {
        val vertex = ops.createVertex("Person", mapOf("name" to "Charlie", "age" to 25L))
        val updated = ops.updateVertex("Person", vertex.id, mapOf("age" to 26L))

        updated.shouldNotBeNull()
        updated.id shouldBeEqualTo vertex.id
        updated.properties["age"] shouldBeEqualTo 26L
    }

    @Test
    @Order(27)
    fun `정점을 삭제한다`() {
        val vertex = ops.createVertex("Person", mapOf("name" to "Dave"))
        val deleted = ops.deleteVertex("Person", vertex.id)

        deleted.shouldBeTrue()
        ops.findVertexById("Person", vertex.id).shouldBeNull()
    }

    @Test
    @Order(28)
    fun `정점 개수를 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))

        val count = ops.countVertices("Person")
        count shouldBeEqualTo 2L
    }

    @Test
    @Order(29)
    fun `transaction은 실패 시 생성한 정점과 간선을 rollback한다`() {
        val existing = ops.createVertex("Person", mapOf("name" to "Existing"))

        assertFailsWith<IllegalStateException> {
            ops.transaction {
                val alice = createVertex("Person", mapOf("name" to "Alice"))
                val bob = createVertex("Person", mapOf("name" to "Bob"))
                createEdge(alice.id, bob.id, "KNOWS")
                error("rollback")
            }
        }

        ops.findVertexById("Person", existing.id)?.properties?.get("name") shouldBeEqualTo "Existing"
        ops.findVerticesByLabel("Person") shouldHaveSize 1
        ops.findEdgesByLabel("KNOWS").shouldBeEmpty()
    }

    @Test
    @Order(29)
    fun `unsafe Cypher identifiers are rejected before query execution`() {
        assertFailsWith<IllegalArgumentException> {
            ops.findVerticesByLabel("Person", mapOf("name) RETURN n MATCH (m" to "Alice"))
        }
        assertFailsWith<IllegalArgumentException> {
            ops.findEdgesByStartId(0.toGraphElementId(), "KNOWS) MATCH (m")
        }
    }

    // ----- 간선(Edge) CRUD -----

    @Test
    @Order(30)
    fun `두 정점 사이에 간선을 생성한다`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))

        val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2020L))

        log.debug { "edge=$edge" }
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo alice.id
        edge.endId shouldBeEqualTo bob.id
        edge.properties["since"] shouldBeEqualTo 2020L
    }

    @Test
    @Order(31)
    fun `label로 간선 목록을 조회한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")
        ops.createEdge(a.id, c.id, "FOLLOWS")

        val knowsEdges = ops.findEdgesByLabel("KNOWS").onEach { log.debug { "person=$it" } }
        knowsEdges shouldHaveSize 2
        knowsEdges.all { it.label == "KNOWS" }.shouldBeTrue()
    }

    @Test
    @Order(32)
    fun `간선을 삭제한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val edge = ops.createEdge(a.id, b.id, "KNOWS")

        val deleted = ops.deleteEdge("KNOWS", edge.id)
        deleted.shouldBeTrue()

        ops.findEdgesByLabel("KNOWS").shouldBeEmpty()
    }

    // ----- 그래프 탐색 (Traversal) -----

    @Test
    @Order(40)
    fun `이웃 정점을 조회한다 - OUTGOING`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))

        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(alice.id, carol.id, "KNOWS")

        val neighbors = ops
            .neighbors(
                alice.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING)
            )
            .onEach { log.debug { "neighbor=$it" } }

        neighbors shouldHaveSize 2

        val names = neighbors.map { it.properties["name"] }
        names shouldContain "Bob"
        names shouldContain "Carol"
    }

    @Test
    @Order(41)
    fun `이웃 정점을 조회한다 - INCOMING`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))

        ops.createEdge(bob.id, alice.id, "KNOWS")
        ops.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = ops
            .neighbors(
                alice.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING)
            )
            .onEach { log.debug { "neighbor=$it" } }

        neighbors.forEach { vertex ->
            log.debug { "neighbor=$vertex" }
        }
        neighbors shouldHaveSize 2

        val names = neighbors.map { it.properties["name"] }
        names shouldContain "Bob"
        names shouldContain "Carol"
    }

    @Test
    @Order(42)
    fun `이웃 정점을 조회한다 - BOTH`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))

        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = ops
            .neighbors(
                alice.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH)
            )
            .onEach { log.debug { "neighbor=$it" } }

        neighbors shouldHaveSize 2

        val names = neighbors.map { it.properties["name"] }
        names shouldContainSame listOf("Bob", "Carol")
    }

    @Test
    @Order(43)
    fun `depth=2로 2단계 이웃을 조회한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")

        val neighbors = ops
            .neighbors(
                a.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
            )
            .onEach { log.debug { "neighbor=$it" } }

        neighbors.shouldNotBeEmpty()

        val names = neighbors.map { it.properties["name"] }
        names shouldContainSame listOf("B", "C")
    }

    @Test
    @Order(50)
    fun `최단 경로를 탐색한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")

        val path = ops
            .shortestPath(
                a.id,
                c.id,
                PathOptions(edgeLabel = "KNOWS")
            ).shouldNotBeNull()

        path.vertices.forEach { log.debug { "vertex=$it" } }
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
    fun `모든 경로를 탐색한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")
        ops.createEdge(a.id, c.id, "KNOWS")

        val paths = ops
            .allPaths(
                a.id,
                c.id,
                PathOptions(edgeLabel = "KNOWS")
            )
            .onEach { log.debug { "path=$it" } }

        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterOrEqualTo 2
    }
}

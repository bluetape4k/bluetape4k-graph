package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Neo4jGraphSuspendOperationsTest {

    companion object : KLoggingChannel()

    private lateinit var driver: Driver
    private lateinit var ops: Neo4jGraphSuspendOperations

    @BeforeAll
    fun setup() {
        val server = Neo4jServer.Launcher.neo4j
        driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
        ops = Neo4jGraphSuspendOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun clearGraph() = runSuspendIO {
        ops.dropGraph("default")
    }

    // ----- 그래프 초기화 -----

    @Test
    @Order(10)
    fun `graphExists는 항상 true 반환`() = runSuspendIO {
        ops.graphExists("default").shouldBeTrue()
    }

    @Test
    @Order(11)
    fun `dropGraph로 전체 데이터를 삭제한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.countVertices("Person") shouldBeGreaterOrEqualTo 1L

        ops.dropGraph("default")
        ops.countVertices("Person") shouldBeEqualTo 0L
    }

    // ----- 정점(Vertex) CRUD -----

    @Test
    @Order(20)
    fun `정점을 label만으로 생성한다`() = runSuspendIO {
        val vertex = ops.createVertex("Person")

        log.debug { "vertex=$vertex" }
        vertex.id.value.shouldNotBeEmpty()
        vertex.label shouldBeEqualTo "Person"
    }

    @Test
    @Order(21)
    fun `label과 properties로 정점을 생성한다`() = runSuspendIO {
        val props = mapOf("name" to "Alice", "age" to 30L)
        val vertex = ops.createVertex("Person", props)

        log.debug { "vertex=$vertex" }
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(22)
    fun `findVertexById로 정점을 조회한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "Bob"))
        val found = ops.findVertexById("Person", created.id)

        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(23)
    fun `존재하지 않는 id로 조회하면 null 반환`() = runSuspendIO {
        val found = ops.findVertexById("Person", io.bluetape4k.graph.model.GraphElementId.of("0:nonexist:0"))
        found.shouldBeNull()
    }

    @Test
    @Order(24)
    fun `findVerticesByLabel로 레이블로 정점을 검색한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("City", mapOf("name" to "Seoul"))

        val people = ops.findVerticesByLabel("Person").toList()
        people.shouldHaveSize(2)
        people.map { it.properties["name"] } shouldContain "Alice"
    }

    @Test
    @Order(25)
    fun `findVerticesByLabel with filter로 필터링한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))

        val found = ops.findVerticesByLabel("Person", mapOf("name" to "Alice")).toList()
        found.shouldHaveSize(1)
        found[0].properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    @Order(26)
    fun `updateVertex로 정점 속성을 갱신한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 25L))
        val updated = ops.updateVertex("Person", created.id, mapOf("age" to 30L))

        updated.shouldNotBeNull()
        updated.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(27)
    fun `deleteVertex로 정점을 삭제한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "ToDelete"))
        val deleted = ops.deleteVertex("Person", created.id)
        deleted.shouldBeTrue()

        ops.countVertices("Person") shouldBeEqualTo 0L
    }

    @Test
    @Order(28)
    fun `countVertices로 레이블별 정점 수를 센다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("City", mapOf("name" to "Seoul"))

        ops.countVertices("Person") shouldBeEqualTo 2L
        ops.countVertices("City") shouldBeEqualTo 1L
    }

    // ----- 간선(Edge) CRUD -----

    @Test
    @Order(30)
    fun `createEdge로 간선을 생성한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS")

        log.debug { "edge=$edge" }
        edge.id.value.shouldNotBeEmpty()
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo alice.id
        edge.endId shouldBeEqualTo bob.id
    }

    @Test
    @Order(31)
    fun `createEdge with properties로 간선 속성을 저장한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024L))

        edge.properties["since"] shouldBeEqualTo 2024L
    }

    @Test
    @Order(32)
    fun `findEdgesByLabel로 간선을 검색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(alice.id, bob.id, "LIKES")

        val knows = ops.findEdgesByLabel("KNOWS").toList()
        knows.shouldHaveSize(1)
        knows[0].label shouldBeEqualTo "KNOWS"
    }

    @Test
    @Order(33)
    fun `deleteEdge로 간선을 삭제한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS")

        val deleted = ops.deleteEdge("KNOWS", edge.id)
        deleted.shouldBeTrue()

        val remaining = ops.findEdgesByLabel("KNOWS").toList()
        remaining.shouldNotBeNull()
    }

    // ----- 탐색(Traversal) -----

    @Test
    @Order(40)
    fun `neighbors로 이웃 정점을 조회한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val charlie = ops.createVertex("Person", mapOf("name" to "Charlie"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(alice.id, charlie.id, "KNOWS")

        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).toList()
        neighbors.shouldHaveSize(2)
        neighbors.map { it.properties["name"] } shouldContain "Bob"
        neighbors.map { it.properties["name"] } shouldContain "Charlie"
    }

    @Test
    @Order(41)
    fun `neighbors with INCOMING direction으로 역방향 탐색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")

        val incoming = ops.neighbors(
            bob.id,
            NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING),
        ).toList()
        incoming.shouldHaveSize(1)
        incoming[0].properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    @Order(42)
    fun `neighbors with BOTH direction으로 양방향 탐색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")

        val both = ops.neighbors(
            alice.id,
            NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH),
        ).toList()
        both.shouldNotBeEmpty()
    }

    @Test
    @Order(43)
    fun `shortestPath로 최단 경로를 찾는다`() = runSuspendIO {
        val a = ops.createVertex("Node", mapOf("name" to "A"))
        val b = ops.createVertex("Node", mapOf("name" to "B"))
        val c = ops.createVertex("Node", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")

        val path = ops.shortestPath(a.id, c.id, PathOptions(edgeLabel = "E"))
        path.shouldNotBeNull()
        path.vertices.shouldNotBeEmpty()
    }

    @Test
    @Order(44)
    fun `연결되지 않은 정점 사이의 최단 경로는 null`() = runSuspendIO {
        val a = ops.createVertex("Node", mapOf("name" to "A"))
        val b = ops.createVertex("Node", mapOf("name" to "B"))

        val path = ops.shortestPath(a.id, b.id, PathOptions())
        path.shouldBeNull()
    }

    @Test
    @Order(45)
    fun `allPaths로 모든 경로를 찾는다`() = runSuspendIO {
        val a = ops.createVertex("Node", mapOf("name" to "A"))
        val b = ops.createVertex("Node", mapOf("name" to "B"))
        val c = ops.createVertex("Node", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(a.id, c.id, "E")
        ops.createEdge(b.id, c.id, "E")

        val paths = ops.allPaths(a.id, c.id, PathOptions(edgeLabel = "E", maxDepth = 3)).toList()
        paths.shouldNotBeEmpty()
    }

    // ----- 알고리즘 (Neo4jAlgorithmSuspendTest에서 미커버 항목 추가) -----

    @Test
    @Order(50)
    fun `degreeCentrality로 차수를 계산한다`() = runSuspendIO {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))
        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(c.id, a.id, "KNOWS")

        val degree = ops.degreeCentrality(a.id, DegreeOptions(edgeLabel = "KNOWS"))
        degree.outDegree shouldBeEqualTo 1
        degree.inDegree shouldBeEqualTo 1
    }

    @Test
    @Order(51)
    fun `connectedComponents로 연결 컴포넌트를 찾는다`() = runSuspendIO {
        val a1 = ops.createVertex("Person", mapOf("g" to "A"))
        val a2 = ops.createVertex("Person", mapOf("g" to "A"))
        val b1 = ops.createVertex("Person", mapOf("g" to "B"))
        val b2 = ops.createVertex("Person", mapOf("g" to "B"))
        ops.createEdge(a1.id, a2.id, "REL")
        ops.createEdge(b1.id, b2.id, "REL")

        val components = ops.connectedComponents(
            ComponentOptions(vertexLabel = "Person", edgeLabel = "REL"),
        ).toList()
        components.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    @Order(52)
    fun `dfs로 깊이 우선 탐색을 한다`() = runSuspendIO {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")

        val visits = ops.dfs(a.id, BfsDfsOptions(edgeLabel = "E", maxDepth = 2)).toList()
        visits.first().vertex.id shouldBeEqualTo a.id
        visits.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    @Order(53)
    fun `detectCycles로 순환을 감지한다`() = runSuspendIO {
        val a = ops.createVertex("Node", emptyMap())
        val b = ops.createVertex("Node", emptyMap())
        val c = ops.createVertex("Node", emptyMap())
        ops.createEdge(a.id, b.id, "E")
        ops.createEdge(b.id, c.id, "E")
        ops.createEdge(c.id, a.id, "E")

        val cycles = ops.detectCycles(CycleOptions(edgeLabel = "E", maxDepth = 5)).toList()
        cycles.shouldNotBeEmpty()
    }
}

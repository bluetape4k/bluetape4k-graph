package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.GraphUnsupportedOperationException
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.graph.repository.suspendTransaction
import io.bluetape4k.junit5.coroutines.SuspendedJobTester
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.utils.Runtimex
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.util.concurrent.atomic.AtomicInteger

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FalkorDBGraphSuspendOperationsTest: AbstractFalkorDBTest() {

    companion object: KLoggingChannel()

    private val mockDriver = mockk<com.falkordb.Driver>()
    private val failingGraph = mockk<com.falkordb.GraphContextGenerator>(relaxed = true)

    @BeforeEach
    override fun beforeEach() {
        super.beforeEach()
        clearMocks(mockDriver, failingGraph)
    }

    // ----- 그래프 초기화 -----

    @Test
    @Order(10)
    fun `createVertex creates vertex with id and label`() = runSuspendIO {
        val vertex = suspendOps.createVertex("Person")

        log.debug { "vertex=$vertex" }
        vertex.id.value.shouldNotBeEmpty()
        vertex.label shouldBeEqualTo "Person"
    }

    @Test
    @Order(11)
    fun `graphExists returns true when graph has data`() = runSuspendIO {
        // dropGraph 후 첫 쿼리로 lazy 생성됨
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.graphExists(graphName).shouldBeTrue()
    }

    @Test
    @Order(12)
    fun `graphExists returns false for non-existent graph`() = runSuspendIO {
        val result = suspendOps.graphExists("non_existent_graph_xyz_12345")
        result.shouldBeFalse()
    }

    @Test
    @Order(13)
    fun `graphExists propagates coroutine cancellation`() = runSuspendIO {
        every { mockDriver.listGraphs() } throws CancellationException("cancelled")

        val cancelledOps = FalkorDBGraphSuspendOperations(mockDriver, graphName)

        assertFailsWith<CancellationException> {
            cancelledOps.graphExists(graphName)
        }
    }

    @Test
    @Order(15)
    fun `dropGraph propagates coroutine cancellation`() = runSuspendIO {
        every { mockDriver.graph(graphName) } throws CancellationException("cancelled")

        val cancelledOps = FalkorDBGraphSuspendOperations(mockDriver, graphName)

        assertFailsWith<CancellationException> {
            cancelledOps.dropGraph(graphName)
        }
    }

    @Test
    @Order(14)
    fun `graphExists preserves backend failures`() = runSuspendIO {
        every { mockDriver.listGraphs() } throws IllegalStateException("redis unavailable")
        val failingOps = FalkorDBGraphSuspendOperations(mockDriver, graphName)

        val ex = assertFailsWith<GraphQueryException> {
            failingOps.graphExists(graphName)
        }

        log.debug { "e=$ex" }
        ex.message shouldContain "FalkorDB graphExists failed"
        ex.cause?.cause shouldBeInstanceOf IllegalStateException::class
    }

    @Test
    @Order(16)
    fun `dropGraph preserves backend failures`() = runSuspendIO {
        every { mockDriver.graph(graphName) } returns failingGraph
        every { failingGraph.deleteGraph() } throws IllegalStateException("redis unavailable")
        val failingOps = FalkorDBGraphSuspendOperations(mockDriver, graphName)

        val ex = assertFailsWith<GraphQueryException> {
            failingOps.dropGraph(graphName)
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "FalkorDB dropGraph failed"
        ex.cause?.cause shouldBeInstanceOf IllegalStateException::class
    }

    // ----- 정점(Vertex) CRUD -----

    @Test
    @Order(20)
    fun `label과 properties로 정점을 생성한다`() = runSuspendIO {
        val props = mapOf("name" to "Alice", "age" to 30L)
        val vertex = suspendOps.createVertex("Person", props)

        log.debug { "vertex=$vertex" }
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(21)
    fun `findVertexById로 생성된 정점을 조회한다`() = runSuspendIO {
        val created = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val found = suspendOps.findVertexById("Person", created.id)

        log.debug { "found=$found" }
        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(22)
    fun `존재하지 않는 id로 조회하면 null 반환`() = runSuspendIO {
        val fakeId = 999_999_999.toGraphElementId()
        val result = suspendOps.findVertexById("Person", fakeId)
        result.shouldBeNull()
    }

    @Test
    @Order(23)
    fun `findVerticesByLabel로 label에 해당하는 정점 목록을 조회한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        suspendOps.createVertex("Car", mapOf("model" to "Tesla"))

        val persons = suspendOps.findVerticesByLabel("Person")
            .log("Person")
            .toList()

        persons shouldHaveSize 2
        persons.all { it.label == "Person" }.shouldBeTrue()
        persons.forEach { person ->
            log.debug { "person=$person" }
        }
    }

    @Test
    @Order(24)
    fun `filter 조건으로 정점을 조회한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        suspendOps.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))

        val result = suspendOps
            .findVerticesByLabel("Person", mapOf("city" to "Seoul"))
            .single()

        result.properties["name"] shouldBeEqualTo "Alice"
        log.debug { "result=$result" }
    }

    @Test
    @Order(25)
    fun `updateVertex로 정점 properties를 업데이트한다`() = runSuspendIO {
        val vertex = suspendOps.createVertex("Person", mapOf("name" to "Charlie", "age" to 25L))
        val updated = suspendOps.updateVertex("Person", vertex.id, mapOf("age" to 26L))

        log.debug { "updated=$updated" }
        updated.shouldNotBeNull()
        updated.id shouldBeEqualTo vertex.id
        updated.properties["age"] shouldBeEqualTo 26L
    }

    @Test
    @Order(26)
    fun `deleteVertex로 정점을 삭제하면 재조회 시 null 반환`() = runSuspendIO {
        val vertex = suspendOps.createVertex("Person", mapOf("name" to "Dave"))
        val deleted = suspendOps.deleteVertex("Person", vertex.id)
        deleted.shouldBeTrue()

        suspendOps.findVertexById("Person", vertex.id).shouldBeNull()
    }

    @Test
    @Order(28)
    fun `suspendTransaction은 중간 결과가 필요한 repository DSL을 지원하지 않는다`() = runSuspendIO {
        val ex = assertFailsWith<GraphUnsupportedOperationException> {
            suspendOps.suspendTransaction {
                createVertex("Person")
            }
        }

        log.debug { "ex=$ex" }
        ex.message shouldContain "does not support suspend graph transactions"
    }

    @Test
    @Order(27)
    fun `countVertices로 정점 개수를 조회한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.createVertex("Person", mapOf("name" to "Bob"))

        val count = suspendOps.countVertices("Person")
        count shouldBeEqualTo 2L
    }

    // ----- 간선(Edge) CRUD -----

    @Test
    @Order(30)
    fun `createEdge로 두 정점 사이에 간선을 생성한다`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))

        val edge = suspendOps.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2020L))

        log.debug { "edge=$edge" }
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo alice.id
        edge.endId shouldBeEqualTo bob.id
        edge.properties["since"] shouldBeEqualTo 2020L
    }

    @Test
    @Order(31)
    fun `findEdgesByLabel로 label에 해당하는 간선 목록을 조회한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")
        suspendOps.createEdge(a.id, c.id, "FOLLOWS")

        val knowsEdges = suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList()
        knowsEdges shouldHaveSize 2
        knowsEdges.all { it.label == "KNOWS" }.shouldBeTrue()
    }

    @Test
    @Order(32)
    fun `deleteEdge로 간선을 삭제하면 재조회 시 empty`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val edge = suspendOps.createEdge(a.id, b.id, "KNOWS")

        suspendOps.deleteEdge("KNOWS", edge.id).shouldBeTrue()
        suspendOps.findEdgesByLabel("KNOWS").toList().shouldBeEmpty()
    }

    // ----- 그래프 탐색 (Traversal) -----

    @Test
    @Order(40)
    fun `neighbors로 OUTGOING 이웃 정점을 조회한다`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val carol = suspendOps.createVertex("Person", mapOf("name" to "Carol"))

        suspendOps.createEdge(alice.id, bob.id, "KNOWS")
        suspendOps.createEdge(alice.id, carol.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING))
            .log("Neighbor")
            .toList()

        neighbors shouldHaveSize 2
        neighbors.map { it.properties["name"] } shouldBeEqualTo listOf("Bob", "Carol")
    }

    @Test
    @Order(41)
    fun `neighbors로 INCOMING 이웃 정점을 조회한다`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val carol = suspendOps.createVertex("Person", mapOf("name" to "Carol"))

        suspendOps.createEdge(bob.id, alice.id, "KNOWS")
        suspendOps.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING))
            .log("Neighbor")
            .toList()

        neighbors shouldHaveSize 2
        neighbors.map { it.properties["name"] } shouldBeEqualTo listOf("Bob", "Carol")
    }

    @Test
    @Order(42)
    fun `neighbors로 BOTH 방향 이웃 정점을 조회한다`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val carol = suspendOps.createVertex("Person", mapOf("name" to "Carol"))

        suspendOps.createEdge(alice.id, bob.id, "KNOWS")
        suspendOps.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH))
            .log("Neighbor")
            .toList()

        neighbors shouldHaveSize 2
        neighbors.map { it.properties["name"] } shouldBeEqualTo listOf("Bob", "Carol")
    }

    @Test
    @Order(43)
    fun `depth=2로 2단계 이웃 정점을 조회한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(
                a.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
            )
            .log("Neighbor")
            .toList()

        neighbors.map { it.properties["name"] } shouldBeEqualTo listOf("B", "C")
    }

    @Test
    @Order(50)
    fun `shortestPath로 최단 경로를 탐색한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")

        val path = suspendOps.shortestPath(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))

        log.debug { "path=$path" }
        path.shouldNotBeNull()
        path.vertices.shouldNotBeEmpty()
    }

    @Test
    @Order(51)
    fun `연결되지 않은 경우 shortestPath는 null 반환`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))

        // 간선 없음
        val path = suspendOps.shortestPath(a.id, b.id, PathOptions(edgeLabel = "KNOWS"))
        path.shouldBeNull()
    }

    @Test
    @Order(52)
    fun `allPaths로 모든 경로를 탐색한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")
        suspendOps.createEdge(a.id, c.id, "KNOWS")

        val paths = suspendOps
            .allPaths(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))
            .log("Paths")
            .toList()


        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterOrEqualTo 2
    }

    // ----- 동시성 시나리오 -----

    @Test
    @Order(60)
    fun `10-way concurrent createVertex succeeds`() = runSuspendIO {
        val results = List(10) { i ->
            async {
                suspendOps.createVertex("Person", mapOf("name" to "user$i"))
            }
        }.awaitAll()

        results shouldHaveSize 10
        results.forEach { it.shouldNotBeNull() }
    }

    @Test
    @Order(61)
    fun `coroutines concurrent createVertex succeeds`() = runSuspendIO {
        val createdCounter = AtomicInteger(0)
        SuspendedJobTester()
            .rounds(Runtimex.availableProcessors * 4)
            .add {
                suspendOps.createVertex("Person", mapOf("name" to "user${createdCounter.get()}"))
                createdCounter.incrementAndGet()
            }
            .run()

        createdCounter.get() shouldBeEqualTo Runtimex.availableProcessors * 4
    }
}

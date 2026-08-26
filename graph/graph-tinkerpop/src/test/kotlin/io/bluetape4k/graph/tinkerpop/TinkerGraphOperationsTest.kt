package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.logging.KLogging
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.util.NoSuchElementException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TinkerGraphOperationsTest {

    companion object: KLogging()

    private val ops = TinkerGraphOperations()

    @AfterAll
    fun teardown() {
        ops.close()
    }

    @BeforeEach
    fun clearGraph() {
        ops.createGraph("default")
        ops.dropGraph("default")
    }

    // ----- 그래프 초기화 -----

    @Test
    @Order(10)
    fun `기본 current graph가 존재하면 true 반환`() {
        ops.graphExists("default").shouldBeTrue()
    }

    @Test
    @Order(12)
    fun `다른 graph name으로 dropGraph하면 현재 graph를 삭제하지 않는다`() {
        ops.createGraph("current")
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.graphExists("current").shouldBeTrue()
        ops.graphExists("other").shouldBeFalse()

        val ex = assertFailsWith<GraphQueryException> {
            ops.dropGraph("other")
        }

        ex.message shouldContain "current"
        ops.countVertices("Person") shouldBeEqualTo 1L
    }

    @Test
    @Order(13)
    fun `동시에 graph를 선택하고 삭제해도 다른 logical graph의 데이터는 지우지 않는다`() {
        repeat(200) { iteration ->
            ops.createGraph("A")
            ops.dropGraph("A")

            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val drop = executor.submit {
                    ready.countDown()
                    start.await()
                    runCatching { ops.dropGraph("A") }
                }
                val select = executor.submit {
                    ready.countDown()
                    start.await()
                    ops.createGraph("B")
                    ops.createVertex("Person", mapOf("iteration" to iteration))
                }

                ready.await(5, TimeUnit.SECONDS)
                start.countDown()
                drop.get(5, TimeUnit.SECONDS)
                select.get(5, TimeUnit.SECONDS)

                ops.countVertices("Person") shouldBeEqualTo 1L
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    @Order(11)
    fun `dropGraph로 전체 데이터를 삭제한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.countVertices("Person") shouldBeGreaterOrEqualTo 1L

        ops.dropGraph("default")

        ops.countVertices("Person") shouldBeEqualTo 0L
    }

    // ----- 정점(Vertex) CRUD -----

    @Test
    @Order(20)
    fun `정점을 생성하면 elementId가 부여된다`() {
        val vertex = ops.createVertex("Person")
        vertex.id.value.shouldNotBeEmpty()
        vertex.label shouldBeEqualTo "Person"
    }

    @Test
    @Order(21)
    fun `label과 properties로 정점을 생성한다`() {
        val props = mapOf("name" to "Alice", "age" to 30L)
        val vertex = ops.createVertex("Person", props)

        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(22)
    fun `id로 정점을 조회한다`() {
        val created = ops.createVertex("Person", mapOf("name" to "Bob"))
        val found = ops.findVertexById("Person", created.id)

        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(23)
    fun `존재하지 않는 id로 조회하면 null 반환`() {
        val fakeId = GraphElementId.of("99999999")
        val result = ops.findVertexById("Person", fakeId)
        result.shouldBeNull()
    }

    @Test
    @Order(231)
    fun `malformed id는 sync repository와 traversal에서 GraphQueryException으로 거부한다`() {
        val malformedId = GraphElementId.of("not-a-number")
        val missingId = GraphElementId.of("99999999")
        val operations = listOf<() -> Any?>(
            { ops.findVertexById("Person", malformedId) },
            { ops.findVertexById(malformedId) },
            { ops.updateVertex("Person", malformedId, emptyMap()) },
            { ops.deleteVertex("Person", malformedId) },
            { ops.findEdgesByStartId(malformedId) },
            { ops.findEdgesByEndId(malformedId) },
            { ops.deleteEdge("KNOWS", malformedId) },
            { ops.neighbors(malformedId, NeighborOptions()) },
            { ops.shortestPath(malformedId, missingId, PathOptions()) },
            { ops.shortestPath(malformedId, missingId, PathOptions(weightProperty = "weight")) },
            { ops.aStarPath(malformedId, missingId, PathOptions(weightProperty = "weight")) { 0.0 } },
            { ops.allPaths(malformedId, missingId, PathOptions()) },
            { ops.degreeCentrality(malformedId, DegreeOptions()) },
            { ops.bfs(malformedId, BfsDfsOptions()) },
            { ops.dfs(malformedId, BfsDfsOptions()) },
        )

        operations.forEach { operation ->
            val failure = assertFailsWith<GraphQueryException> { operation() }
            failure.message shouldContain "numeric ID"
        }
    }

    @Test
    @Order(232)
    fun `valid but missing numeric id는 absence semantics를 유지한다`() {
        val missingId = GraphElementId.of("99999999")

        ops.findVertexById("Person", missingId).shouldBeNull()
        ops.updateVertex("Person", missingId, emptyMap()).shouldBeNull()
        ops.deleteVertex("Person", missingId).shouldBeFalse()
        ops.findEdgesByStartId(missingId).shouldBeEmpty()
        ops.findEdgesByEndId(missingId).shouldBeEmpty()
        ops.deleteEdge("KNOWS", missingId).shouldBeFalse()
        ops.neighbors(missingId, NeighborOptions()).shouldBeEmpty()
        ops.shortestPath(missingId, missingId, PathOptions()).shouldBeNull()
        ops.allPaths(missingId, missingId, PathOptions()).shouldBeEmpty()
    }

    @Test
    @Order(24)
    fun `label로 정점 목록을 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("Car", mapOf("model" to "Tesla"))

        val persons = ops.findVerticesByLabel("Person")
        persons.shouldHaveSize(2)
        persons.all { it.label == "Person" }.shouldBeTrue()
    }

    @Test
    @Order(25)
    fun `filter 조건으로 정점을 조회한다`() {
        ops.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        ops.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))

        val result = ops.findVerticesByLabel("Person", mapOf("city" to "Seoul"))
        result.shouldHaveSize(1)
        result[0].properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    @Order(251)
    fun `label로 정점을 chunk 단위로 조회한다`() {
        (1..5).forEach { index ->
            ops.createVertex("Person", mapOf("name" to "Person-$index"))
        }

        val chunks = ops.findVerticesByLabelChunked("Person", chunkSize = 2).toList()

        chunks.map { it.size } shouldBeEqualTo listOf(2, 2, 1)
        chunks.flatten().map { it.properties["name"] }.toSet() shouldBeEqualTo
                setOf("Person-1", "Person-2", "Person-3", "Person-4", "Person-5")
    }

    @Test
    @Order(252)
    fun `bounded chunk helper consumes only the requested first chunk`() {
        var consumed = 0
        var closed = false
        val source = object : Iterator<Int> {
            private var nextValue = 0

            override fun hasNext(): Boolean = nextValue < 100

            override fun next(): Int {
                if (!hasNext()) throw NoSuchElementException()
                consumed++
                return ++nextValue
            }
        }

        val chunks = sequence<List<Int>> {
            with(ops) {
                yieldMappedChunks(
                    source = source,
                    chunkSize = 2,
                    mapper = { it },
                    close = { closed = true },
                )
            }
        }.take(1).toList()

        chunks shouldBeEqualTo listOf(listOf(1, 2))
        consumed shouldBeEqualTo 2

        val fullyConsumed = sequence<List<Int>> {
            with(ops) {
                yieldMappedChunks(
                    source = (1..3).iterator(),
                    chunkSize = 2,
                    mapper = { it },
                    close = { closed = true },
                )
            }
        }.toList()

        fullyConsumed shouldBeEqualTo listOf(listOf(1, 2), listOf(3))
        closed.shouldBeTrue()
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

    // ----- 간선(Edge) CRUD -----

    @Test
    @Order(30)
    fun `두 정점 사이에 간선을 생성한다`() {
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
    fun `label로 간선 목록을 조회한다`() {
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
    @Order(311)
    fun `label로 간선을 chunk 단위로 조회한다`() {
        val vertices = (1..4).map { index ->
            ops.createVertex("Person", mapOf("name" to "Person-$index"))
        }
        (0..2).forEach { index ->
            ops.createEdge(vertices[index].id, vertices[index + 1].id, "KNOWS", mapOf("rank" to index))
        }

        val chunks = ops.findEdgesByLabelChunked("KNOWS", chunkSize = 2).toList()

        chunks.map { it.size } shouldBeEqualTo listOf(2, 1)
        chunks.flatten().map { it.properties["rank"] }.toSet() shouldBeEqualTo setOf(0, 1, 2)
    }

    @Test
    @Order(32)
    fun `간선을 삭제한다`() {
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
    fun `이웃 정점을 조회한다 - OUTGOING`() {
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
    fun `이웃 정점을 조회한다 - INCOMING`() {
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
    fun `이웃 정점을 조회한다 - BOTH`() {
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
    fun `depth=2로 2단계 이웃을 조회한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")

        val neighbors =
            ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2))
        neighbors.shouldNotBeEmpty()
        val names = neighbors.map { it.properties["name"] }
        names shouldContain "B"
        names shouldContain "C"
    }

    @Test
    @Order(50)
    fun `최단 경로를 탐색한다`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        val c = ops.createVertex("Person", mapOf("name" to "C"))

        ops.createEdge(a.id, b.id, "KNOWS")
        ops.createEdge(b.id, c.id, "KNOWS")

        val path = ops.shortestPath(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))
        path.shouldNotBeNull()
        path.vertices.shouldNotBeEmpty()
        path.edges shouldHaveSize 2
        path.length shouldBeEqualTo 2
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

        val paths = ops.allPaths(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))
        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterOrEqualTo 2
    }

    @Test
    @Order(53)
    fun `연결되지 않은 경우 allPaths는 빈 리스트 반환`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))

        // 간선 없음
        val paths = ops.allPaths(a.id, b.id, PathOptions(edgeLabel = "KNOWS"))
        paths.shouldBeEmpty()
    }

    @Test
    @Order(60)
    fun `간선 없는 정점의 이웃은 빈 리스트 반환`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))

        // 간선 없음 - 이웃이 없어야 함
        val neighbors = ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING))
        neighbors.shouldBeEmpty()
    }

    @Test
    @Order(61)
    fun `존재하지 않는 label로 정점 조회 시 빈 리스트 반환`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))

        val result = ops.findVerticesByLabel("NonExistentLabel")
        result.shouldBeEmpty()
    }

    @Test
    @Order(62)
    fun `매칭되지 않는 filter로 조회하면 빈 리스트 반환`() {
        ops.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))

        val result = ops.findVerticesByLabel("Person", mapOf("city" to "Busan"))
        result.shouldBeEmpty()
    }

    @Test
    @Order(63)
    fun `존재하지 않는 label로 countVertices 시 0 반환`() {
        ops.createVertex("Person", mapOf("name" to "Alice"))

        val count = ops.countVertices("NonExistentLabel")
        count shouldBeEqualTo 0L
    }

    @Test
    @Order(64)
    fun `존재하지 않는 label로 간선 조회 시 빈 리스트 반환`() {
        val a = ops.createVertex("Person", mapOf("name" to "A"))
        val b = ops.createVertex("Person", mapOf("name" to "B"))
        ops.createEdge(a.id, b.id, "KNOWS")

        val result = ops.findEdgesByLabel("NON_EXISTENT_EDGE")
        result.shouldBeEmpty()
    }
}

package io.bluetape4k.graph.neo4j

import com.github.benmanes.caffeine.cache.Ticker
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.repository.transaction
import io.bluetape4k.logging.KLogging
import io.bluetape4k.assertions.assertFailsWith
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [CachingNeo4jGraphOperations] 의 읽기 캐시 히트·무효화와 생성 위임 계약을 검증한다.
 *
 * MockK 로 delegate 를 모킹하여 delegate 호출 횟수를 정확히 검증한다.
 * 실제 Neo4j 서버 없이 순수 캐시 레이어만 테스트한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachingNeo4jGraphOperationsTest {

    private class TestTicker : Ticker {
        private var nanos = 0L

        override fun read(): Long = nanos

        fun advance(duration: Duration) {
            nanos += duration.toNanos()
        }
    }

    companion object : KLogging()

    private lateinit var delegate: Neo4jGraphOperations
    private lateinit var caching: CachingNeo4jGraphOperations

    private val aliceId = GraphElementId.of("alice-1")
    private val bobId = GraphElementId.of("bob-1")
    private val edgeId = GraphElementId.of("edge-1")

    private val alice = GraphVertex(aliceId, "Person", mapOf("name" to "Alice"))
    private val bob = GraphVertex(bobId, "Person", mapOf("name" to "Bob"))
    private val edge = GraphEdge(edgeId, "KNOWS", aliceId, bobId)
    private val path = GraphPath(
        listOf(
            PathStep.VertexStep(alice),
            PathStep.EdgeStep(edge),
            PathStep.VertexStep(bob),
        )
    )

    @BeforeEach
    fun setup() {
        delegate = mockk(relaxed = true)
        caching = CachingNeo4jGraphOperations(delegate)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(delegate)
    }

    // ───── findVertexById 캐싱 ─────

    @Test
    fun `findVertexById는 캐시 히트 시 delegate를 1회만 호출한다`() {
        every { delegate.findVertexById("Person", aliceId) } returns alice

        val first = caching.findVertexById("Person", aliceId)
        val second = caching.findVertexById("Person", aliceId)

        first shouldBeEqualTo alice
        second shouldBeEqualTo alice
        verify(exactly = 1) { delegate.findVertexById("Person", aliceId) }
    }

    @Test
    fun `findVertexById는 null 결과도 캐시하여 delegate를 1회만 호출한다`() {
        every { delegate.findVertexById("Person", aliceId) } returns null

        val first = caching.findVertexById("Person", aliceId)
        val second = caching.findVertexById("Person", aliceId)

        first.shouldBeNull()
        second.shouldBeNull()
        verify(exactly = 1) { delegate.findVertexById("Person", aliceId) }
    }

    // ───── findVerticesByLabel 캐싱 ─────

    @Test
    fun `findVerticesByLabel는 캐시 히트 시 delegate를 1회만 호출한다`() {
        val vertices = listOf(alice, bob)
        every { delegate.findVerticesByLabel("Person", emptyMap()) } returns vertices

        val first = caching.findVerticesByLabel("Person")
        val second = caching.findVerticesByLabel("Person")

        first shouldBeEqualTo vertices
        second shouldBeEqualTo vertices
        verify(exactly = 1) { delegate.findVerticesByLabel("Person", emptyMap()) }
    }

    @Test
    fun `findVerticesByLabel는 filter 포함 캐시 히트 시 delegate를 1회만 호출한다`() {
        val filter = mapOf("name" to "Alice")
        every { delegate.findVerticesByLabel("Person", filter) } returns listOf(alice)

        val first = caching.findVerticesByLabel("Person", filter)
        val second = caching.findVerticesByLabel("Person", filter)

        first shouldBeEqualTo listOf(alice)
        second shouldBeEqualTo listOf(alice)
        verify(exactly = 1) { delegate.findVerticesByLabel("Person", filter) }
    }

    // ───── neighbors 캐싱 ─────

    @Test
    fun `neighbors는 캐시 히트 시 delegate를 1회만 호출한다`() {
        val opts = NeighborOptions.Default
        every { delegate.neighbors(aliceId, opts) } returns listOf(bob)

        val first = caching.neighbors(aliceId, opts)
        val second = caching.neighbors(aliceId, opts)

        first shouldBeEqualTo listOf(bob)
        second shouldBeEqualTo listOf(bob)
        verify(exactly = 1) { delegate.neighbors(aliceId, opts) }
    }

    // ───── shortestPath 캐싱 ─────

    @Test
    fun `shortestPath는 캐시 히트 시 delegate를 1회만 호출한다`() {
        val opts = PathOptions.Default
        every { delegate.shortestPath(aliceId, bobId, opts) } returns path

        val first = caching.shortestPath(aliceId, bobId, opts)
        val second = caching.shortestPath(aliceId, bobId, opts)

        first shouldBeEqualTo path
        second shouldBeEqualTo path
        verify(exactly = 1) { delegate.shortestPath(aliceId, bobId, opts) }
    }

    @Test
    fun `shortestPath는 null 결과도 캐시하여 delegate를 1회만 호출한다`() {
        val opts = PathOptions.Default
        every { delegate.shortestPath(aliceId, bobId, opts) } returns null

        val first = caching.shortestPath(aliceId, bobId, opts)
        val second = caching.shortestPath(aliceId, bobId, opts)

        first.shouldBeNull()
        second.shouldBeNull()
        verify(exactly = 1) { delegate.shortestPath(aliceId, bobId, opts) }
    }

    // ───── allPaths 캐싱 ─────

    @Test
    fun `allPaths는 캐시 히트 시 delegate를 1회만 호출한다`() {
        val opts = PathOptions.Default
        every { delegate.allPaths(aliceId, bobId, opts) } returns listOf(path)

        val first = caching.allPaths(aliceId, bobId, opts)
        val second = caching.allPaths(aliceId, bobId, opts)

        first shouldBeEqualTo listOf(path)
        second shouldBeEqualTo listOf(path)
        verify(exactly = 1) { delegate.allPaths(aliceId, bobId, opts) }
    }

    // ───── findEdgesByLabel 캐싱 ─────

    @Test
    fun `findEdgesByLabel는 캐시 히트 시 delegate를 1회만 호출한다`() {
        every { delegate.findEdgesByLabel("KNOWS", emptyMap()) } returns listOf(edge)

        val first = caching.findEdgesByLabel("KNOWS")
        val second = caching.findEdgesByLabel("KNOWS")

        first shouldBeEqualTo listOf(edge)
        second shouldBeEqualTo listOf(edge)
        verify(exactly = 1) { delegate.findEdgesByLabel("KNOWS", emptyMap()) }
    }

    @Test
    fun `findEdgesByLabel는 filter 포함 캐시 히트 시 delegate를 1회만 호출한다`() {
        val filter = mapOf("since" to 2020)
        val filteredEdge = GraphEdge(edgeId, "KNOWS", aliceId, bobId, filter)
        every { delegate.findEdgesByLabel("KNOWS", filter) } returns listOf(filteredEdge)

        val first = caching.findEdgesByLabel("KNOWS", filter)
        val second = caching.findEdgesByLabel("KNOWS", filter)

        first shouldBeEqualTo listOf(filteredEdge)
        second shouldBeEqualTo listOf(filteredEdge)
        verify(exactly = 1) { delegate.findEdgesByLabel("KNOWS", filter) }
    }

    // ───── 쓰기 시 읽기 캐시 무효화 ─────

    @Test
    fun `동시 miss 중 성공한 write가 이전 결과를 캐시에 재적재하지 않는다`() {
        val updated = alice.copy(properties = mapOf("name" to "Alice Updated"))
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        every { delegate.findVertexById("Person", aliceId) } answers {
            readStarted.countDown()
            releaseRead.await(5, TimeUnit.SECONDS)
            alice
        } andThen updated
        every { delegate.updateVertex("Person", aliceId, any()) } returns updated

        val executor = Executors.newSingleThreadExecutor()
        try {
            val pendingRead = executor.submit<GraphVertex?> {
                caching.findVertexById("Person", aliceId)
            }
            readStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

            caching.updateVertex("Person", aliceId, mapOf("name" to "Alice Updated"))
            releaseRead.countDown()
            pendingRead.get(5, TimeUnit.SECONDS) shouldBeEqualTo alice

            caching.findVertexById("Person", aliceId) shouldBeEqualTo updated
            verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `dropGraph 성공 후 읽기 캐시를 무효화한다`() {
        every { delegate.findVertexById("Person", aliceId) } returns alice andThen null

        caching.findVertexById("Person", aliceId)
        caching.dropGraph("default")

        caching.findVertexById("Person", aliceId).shouldBeNull()
        verify(exactly = 1) { delegate.dropGraph("default") }
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
    }

    @Test
    fun `transaction commit 후 읽기 캐시를 무효화하고 rollback 후에는 유지한다`() {
        val updated = alice.copy(properties = mapOf("name" to "Alice Updated"))
        every { delegate.findVertexById("Person", aliceId) } returns alice andThen updated
        every { delegate.transaction<GraphVertex>(any()) } returns updated

        caching.findVertexById("Person", aliceId)
        val committed = caching.transaction { updated }
        committed shouldBeEqualTo updated
        caching.findVertexById("Person", aliceId) shouldBeEqualTo updated
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }

        val failure = IllegalStateException("rollback")
        every { delegate.transaction<GraphVertex>(any()) } throws failure
        caching.findVertexById("Person", aliceId)
        assertFailsWith<IllegalStateException> {
            caching.transaction { error("transaction block should not execute") }
        }
        caching.findVertexById("Person", aliceId) shouldBeEqualTo updated
        verify(exactly = 2) { delegate.transaction<GraphVertex>(any()) }
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
    }

    @Test
    fun `deleteVertex 후 읽기 캐시가 무효화된다`() {
        every { delegate.findVertexById("Person", aliceId) } returns alice andThen null
        every { delegate.deleteVertex("Person", aliceId) } returns true

        caching.findVertexById("Person", aliceId)   // 캐시 적재
        caching.deleteVertex("Person", aliceId)     // 전체 캐시 무효화
        val after = caching.findVertexById("Person", aliceId)  // delegate 재호출

        after.shouldBeNull()
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
    }

    @Test
    fun `updateVertex 후 읽기 캐시가 무효화된다`() {
        val updated = alice.copy(properties = mapOf("name" to "Alice Updated"))
        every { delegate.findVertexById("Person", aliceId) } returns alice andThen updated
        every { delegate.updateVertex("Person", aliceId, any()) } returns updated

        caching.findVertexById("Person", aliceId)   // 캐시 적재
        caching.updateVertex("Person", aliceId, mapOf("name" to "Alice Updated"))
        val after = caching.findVertexById("Person", aliceId)  // delegate 재호출

        after shouldBeEqualTo updated
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
    }

    @Test
    fun `deleteEdge 후 모든 캐시가 무효화된다`() {
        every { delegate.findVerticesByLabel("Person", emptyMap()) } returns listOf(alice) andThen emptyList()
        every { delegate.deleteEdge("KNOWS", edgeId) } returns true

        caching.findVerticesByLabel("Person")   // 캐시 적재
        caching.deleteEdge("KNOWS", edgeId)     // 전체 캐시 무효화
        val after = caching.findVerticesByLabel("Person")  // delegate 재호출

        after shouldBeEqualTo emptyList()
        verify(exactly = 2) { delegate.findVerticesByLabel("Person", emptyMap()) }
    }

    @Test
    fun `deleteVertex 후 여섯 read cache가 모두 무효화된다`() {
        val options = PathOptions.Default
        every { delegate.findVertexById("Person", aliceId) } returns alice andThen bob
        every { delegate.findVerticesByLabel("Person", emptyMap()) } returns listOf(alice) andThen listOf(bob)
        every { delegate.neighbors(aliceId, NeighborOptions.Default) } returns listOf(bob) andThen listOf(alice)
        every { delegate.shortestPath(aliceId, bobId, options) } returns path andThen null
        every { delegate.allPaths(aliceId, bobId, options) } returns listOf(path) andThen emptyList()
        every { delegate.findEdgesByLabel("KNOWS", emptyMap()) } returns listOf(edge) andThen emptyList()
        every { delegate.deleteVertex("Person", aliceId) } returns true

        caching.findVertexById("Person", aliceId) shouldBeEqualTo alice
        caching.findVerticesByLabel("Person") shouldBeEqualTo listOf(alice)
        caching.neighbors(aliceId, NeighborOptions.Default) shouldBeEqualTo listOf(bob)
        caching.shortestPath(aliceId, bobId, options) shouldBeEqualTo path
        caching.allPaths(aliceId, bobId, options) shouldBeEqualTo listOf(path)
        caching.findEdgesByLabel("KNOWS") shouldBeEqualTo listOf(edge)

        caching.deleteVertex("Person", aliceId)

        caching.findVertexById("Person", aliceId) shouldBeEqualTo bob
        caching.findVerticesByLabel("Person") shouldBeEqualTo listOf(bob)
        caching.neighbors(aliceId, NeighborOptions.Default) shouldBeEqualTo listOf(alice)
        caching.shortestPath(aliceId, bobId, options).shouldBeNull()
        caching.allPaths(aliceId, bobId, options) shouldBeEqualTo emptyList()
        caching.findEdgesByLabel("KNOWS") shouldBeEqualTo emptyList()

        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
        verify(exactly = 2) { delegate.findVerticesByLabel("Person", emptyMap()) }
        verify(exactly = 2) { delegate.neighbors(aliceId, NeighborOptions.Default) }
        verify(exactly = 2) { delegate.shortestPath(aliceId, bobId, options) }
        verify(exactly = 2) { delegate.allPaths(aliceId, bobId, options) }
        verify(exactly = 2) { delegate.findEdgesByLabel("KNOWS", emptyMap()) }
    }

    // ───── 생성 계약과 읽기 캐시 무효화 ─────

    @Test
    fun `createVertex는 동일 인자 반복 호출마다 delegate를 호출한다`() {
        val props = mapOf("name" to "Alice")
        val secondVertex = alice.copy(id = GraphElementId.of("alice-2"))
        every { delegate.createVertex("Person", props) } returns alice andThen secondVertex

        val first = caching.createVertex("Person", props)
        val second = caching.createVertex("Person", props)

        first shouldBeEqualTo alice
        second shouldBeEqualTo secondVertex
        verify(exactly = 2) { delegate.createVertex("Person", props) }
    }

    @Test
    fun `createEdge는 동일 인자 반복 호출마다 delegate를 호출한다`() {
        val secondEdge = edge.copy(id = GraphElementId.of("edge-2"))
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge andThen secondEdge

        val first = caching.createEdge(aliceId, bobId, "KNOWS", emptyMap())
        val second = caching.createEdge(aliceId, bobId, "KNOWS", emptyMap())

        first shouldBeEqualTo edge
        second shouldBeEqualTo secondEdge
        verify(exactly = 2) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
    }

    @Test
    fun `createEdge는 생성마다 위임하고 읽기 캐시를 무효화한다`() {
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge andThen
            edge.copy(id = GraphElementId.of("edge-2"))
        every { delegate.findEdgesByLabel("KNOWS", emptyMap()) } returns listOf(edge)

        caching.findEdgesByLabel("KNOWS")              // 읽기 캐시 적재
        caching.createEdge(aliceId, bobId, "KNOWS")    // 읽기 캐시 무효화
        caching.findEdgesByLabel("KNOWS")              // 읽기 캐시 미스 → delegate 재호출

        verify(exactly = 1) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
        verify(exactly = 2) { delegate.findEdgesByLabel("KNOWS", emptyMap()) }

        // 동일 인자라도 생성 요청은 다시 delegate에 위임한다.
        caching.createEdge(aliceId, bobId, "KNOWS")
        verify(exactly = 2) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
    }

    @Test
    fun `createVertex는 생성마다 위임하고 읽기 캐시를 무효화한다`() {
        val props = mapOf("name" to "Alice")
        every { delegate.createVertex("Person", props) } returns alice andThen alice.copy(id = GraphElementId.of("alice-2"))
        every { delegate.findVertexById("Person", aliceId) } returns alice

        caching.findVertexById("Person", aliceId)   // 읽기 캐시 적재
        caching.createVertex("Person", props)        // 읽기 캐시 무효화
        caching.findVertexById("Person", aliceId)   // 읽기 캐시 미스 → delegate 재호출

        verify(exactly = 1) { delegate.createVertex("Person", props) }
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }

        // 동일 인자라도 생성 요청은 다시 delegate에 위임한다.
        caching.createVertex("Person", props)
        verify(exactly = 2) { delegate.createVertex("Person", props) }
    }

    @Test
    fun `createVertices 후 읽기 캐시가 무효화되고 이후 생성도 위임된다`() {
        val props = mapOf("name" to "Alice")
        val batch = listOf(props, mapOf("name" to "Bob"))
        every { delegate.findVertexById("Person", aliceId) } returns alice
        every { delegate.createVertex("Person", props) } returns alice andThen alice.copy(id = GraphElementId.of("alice-2"))
        every { delegate.createVertices("Person", batch) } returns listOf(alice, bob)

        caching.findVertexById("Person", aliceId)
        caching.createVertex("Person", props)
        caching.createVertices("Person", batch)
        caching.findVertexById("Person", aliceId)
        caching.createVertex("Person", props)

        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
        verify(exactly = 2) { delegate.createVertex("Person", props) }
        verify(exactly = 1) { delegate.createVertices("Person", batch) }
    }

    @Test
    fun `createEdges 후 읽기 캐시가 무효화되고 이후 생성도 위임된다`() {
        val batch = listOf(
            BatchEdge(aliceId, bobId),
            BatchEdge(bobId, aliceId, mapOf("since" to 2025L)),
        )
        every { delegate.findEdgesByLabel("KNOWS", emptyMap()) } returns listOf(edge)
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge andThen
            edge.copy(id = GraphElementId.of("edge-2"))
        every { delegate.createEdges("KNOWS", batch) } returns listOf(edge, edge.copy(id = GraphElementId.of("edge-3")))

        caching.findEdgesByLabel("KNOWS")
        caching.createEdge(aliceId, bobId, "KNOWS")
        caching.createEdges("KNOWS", batch)
        caching.findEdgesByLabel("KNOWS")
        caching.createEdge(aliceId, bobId, "KNOWS")

        verify(exactly = 2) { delegate.findEdgesByLabel("KNOWS", emptyMap()) }
        verify(exactly = 2) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
        verify(exactly = 1) { delegate.createEdges("KNOWS", batch) }
    }

    @Test
    fun `createVertex는 다른 속성 맵으로 호출 시 delegate를 각각 호출한다`() {
        val propsAlice = mapOf("name" to "Alice")
        val propsBob = mapOf("name" to "Bob")
        every { delegate.createVertex("Person", propsAlice) } returns alice
        every { delegate.createVertex("Person", propsBob) } returns bob

        val r1 = caching.createVertex("Person", propsAlice)
        val r2 = caching.createVertex("Person", propsBob)

        r1 shouldBeEqualTo alice
        r2 shouldBeEqualTo bob
        verify(exactly = 1) { delegate.createVertex("Person", propsAlice) }
        verify(exactly = 1) { delegate.createVertex("Person", propsBob) }
    }

    @Test
    fun `createEdge는 다른 속성 맵으로 호출 시 delegate를 각각 호출한다`() {
        val edge2 = GraphEdge(GraphElementId.of("edge-2"), "KNOWS", aliceId, bobId, mapOf("since" to 2025))
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge
        every { delegate.createEdge(aliceId, bobId, "KNOWS", mapOf("since" to 2025)) } returns edge2

        val r1 = caching.createEdge(aliceId, bobId, "KNOWS", emptyMap())
        val r2 = caching.createEdge(aliceId, bobId, "KNOWS", mapOf("since" to 2025))

        r1 shouldBeEqualTo edge
        r2 shouldBeEqualTo edge2
        verify(exactly = 1) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
        verify(exactly = 1) { delegate.createEdge(aliceId, bobId, "KNOWS", mapOf("since" to 2025)) }
    }

    // ───── bounded/expiring read cache ─────

    @Test
    fun `maxSize는 단일 read cache의 엔트리 수를 제한한다`() {
        val bounded = CachingNeo4jGraphOperations(delegate, maxSize = 1)
        every { delegate.findVertexById("Person", aliceId) } returns alice
        every { delegate.findVertexById("Person", bobId) } returns bob

        bounded.findVertexById("Person", aliceId)
        bounded.findVertexById("Person", bobId)
        bounded.findVertexById("Person", aliceId)
        bounded.findVertexById("Person", bobId)

        // Caffeine W-TinyLFU 정책은 두 키 중 어느 것을 eviction victim으로 선택할 수 있다.
        // victim 선택에 의존하지 않고 키별 호출 범위와 전체 miss 범위를 검증한다.
        verify(atLeast = 1, atMost = 2) { delegate.findVertexById("Person", aliceId) }
        verify(atLeast = 1, atMost = 2) { delegate.findVertexById("Person", bobId) }
        verify(atLeast = 3, atMost = 4) { delegate.findVertexById("Person", any()) }
    }

    @Test
    fun `expireAfterWrite 후 read cache가 만료되어 delegate를 다시 호출한다`() {
        val ticker = TestTicker()
        val expiring = CachingNeo4jGraphOperations(
            delegate,
            expireAfterWrite = Duration.ofSeconds(1),
            ticker = ticker,
        )
        every { delegate.findVertexById("Person", aliceId) } returns alice

        expiring.findVertexById("Person", aliceId)
        ticker.advance(Duration.ofSeconds(1))
        expiring.findVertexById("Person", aliceId)

        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }
    }

    @Test
    fun `maxSize는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            CachingNeo4jGraphOperations(delegate, maxSize = 0)
        }
    }

    @Test
    fun `expireAfterWrite는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            CachingNeo4jGraphOperations(delegate, expireAfterWrite = Duration.ZERO)
        }
    }
}

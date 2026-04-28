package io.bluetape4k.graph.age

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * [CachingAgeGraphOperations] 의 캐시 히트·무효화·Write 메모이제이션 동작을 검증한다.
 *
 * MockK 로 delegate 를 모킹하여 delegate 호출 횟수를 정확히 검증한다.
 * 실제 PostgreSQL AGE 서버 없이 순수 캐시 레이어만 테스트한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachingAgeGraphOperationsTest {

    companion object : KLogging()

    private lateinit var delegate: AgeGraphOperations
    private lateinit var caching: CachingAgeGraphOperations

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
        caching = CachingAgeGraphOperations(delegate)
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

    // ───── 쓰기 메모이제이션 ─────

    @Test
    fun `createVertex는 동일 인자 반복 호출 시 메모이제이션된 결과를 반환한다`() {
        val props = mapOf("name" to "Alice")
        every { delegate.createVertex("Person", props) } returns alice

        val first = caching.createVertex("Person", props)
        val second = caching.createVertex("Person", props)

        first shouldBeEqualTo alice
        second shouldBeEqualTo alice
        verify(exactly = 1) { delegate.createVertex("Person", props) }
    }

    @Test
    fun `createEdge는 동일 인자 반복 호출 시 메모이제이션된 결과를 반환한다`() {
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge

        val first = caching.createEdge(aliceId, bobId, "KNOWS", emptyMap())
        val second = caching.createEdge(aliceId, bobId, "KNOWS", emptyMap())

        first shouldBeEqualTo edge
        second shouldBeEqualTo edge
        verify(exactly = 1) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
    }

    @Test
    fun `createEdge는 읽기 캐시만 무효화하고 쓰기 메모이제이션 캐시는 보존한다`() {
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge
        every { delegate.findEdgesByLabel("KNOWS", emptyMap()) } returns listOf(edge)

        caching.findEdgesByLabel("KNOWS")              // 읽기 캐시 적재
        caching.createEdge(aliceId, bobId, "KNOWS")    // 읽기 캐시만 무효화, 쓰기 캐시는 유지
        caching.findEdgesByLabel("KNOWS")              // 읽기 캐시 미스 → delegate 재호출

        verify(exactly = 1) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
        verify(exactly = 2) { delegate.findEdgesByLabel("KNOWS", emptyMap()) }

        // 두 번째 createEdge 호출 → 메모이제이션 캐시 히트
        caching.createEdge(aliceId, bobId, "KNOWS")
        verify(exactly = 1) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }  // delegate 추가 호출 없음
    }

    @Test
    fun `createVertex는 읽기 캐시만 무효화하고 쓰기 메모이제이션 캐시는 보존한다`() {
        val props = mapOf("name" to "Alice")
        every { delegate.createVertex("Person", props) } returns alice
        every { delegate.findVertexById("Person", aliceId) } returns alice

        caching.findVertexById("Person", aliceId)   // 읽기 캐시 적재
        caching.createVertex("Person", props)        // 읽기 캐시만 무효화, 쓰기 캐시는 유지
        caching.findVertexById("Person", aliceId)   // 읽기 캐시 미스 → delegate 재호출

        verify(exactly = 1) { delegate.createVertex("Person", props) }
        verify(exactly = 2) { delegate.findVertexById("Person", aliceId) }

        // 두 번째 createVertex 호출 → 메모이제이션 캐시 히트
        caching.createVertex("Person", props)
        verify(exactly = 1) { delegate.createVertex("Person", props) }  // delegate 추가 호출 없음
    }

    @Test
    fun `deleteVertex 후 쓰기 메모이제이션 캐시도 무효화된다`() {
        val props = mapOf("name" to "Alice")
        every { delegate.createVertex("Person", props) } returns alice andThen alice.copy(id = GraphElementId.of("alice-2"))
        every { delegate.deleteVertex("Person", aliceId) } returns true

        caching.createVertex("Person", props)       // 쓰기 캐시 적재
        val deleted = caching.deleteVertex("Person", aliceId)  // 전체 캐시 무효화
        caching.createVertex("Person", props)       // 쓰기 캐시 미스 → delegate 재호출

        deleted.shouldBeTrue()
        verify(exactly = 2) { delegate.createVertex("Person", props) }
    }

    @Test
    fun `updateVertex 후 쓰기 메모이제이션 캐시도 무효화된다`() {
        val props = mapOf("name" to "Alice")
        every { delegate.createVertex("Person", props) } returns alice andThen alice.copy(id = GraphElementId.of("alice-2"))
        every { delegate.updateVertex("Person", aliceId, any()) } returns alice.copy(properties = mapOf("name" to "Alice Updated"))

        caching.createVertex("Person", props)       // 쓰기 캐시 적재
        caching.updateVertex("Person", aliceId, mapOf("name" to "Alice Updated"))  // 전체 캐시 무효화
        caching.createVertex("Person", props)       // 쓰기 캐시 미스 → delegate 재호출

        verify(exactly = 2) { delegate.createVertex("Person", props) }
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
    fun `deleteEdge 후 쓰기 메모이제이션 캐시도 무효화된다`() {
        every { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) } returns edge andThen
            GraphEdge(GraphElementId.of("edge-2"), "KNOWS", aliceId, bobId)
        every { delegate.deleteEdge("KNOWS", edgeId) } returns true

        caching.createEdge(aliceId, bobId, "KNOWS")    // 쓰기 캐시 적재
        val deleted = caching.deleteEdge("KNOWS", edgeId)  // 전체 캐시 무효화
        caching.createEdge(aliceId, bobId, "KNOWS")    // 쓰기 캐시 미스 → delegate 재호출

        deleted.shouldBeTrue()
        verify(exactly = 2) { delegate.createEdge(aliceId, bobId, "KNOWS", emptyMap()) }
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
}

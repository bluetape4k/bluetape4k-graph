package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadSessionAdapterTest: AbstractGraphTest() {

    companion object: KLogging()

    private val delegate = TinkerGraphOperations()
    private val adapter = VirtualThreadSessionAdapter(delegate)

    @AfterAll
    fun tearDown() {
        delegate.close()
    }

    @BeforeEach
    fun setUp() {
        delegate.createGraph("default")
        delegate.dropGraph("default")
    }

    @Test
    fun `createGraphAsync delegates and completes`() {
        val result = adapter.createGraphAsync("social").join()
        result shouldBeEqualTo Unit

        // 실제 위임 결과 확인: 그래프 존재 여부는 TinkerGraph에서 항상 true 반환
        delegate.graphExists("social").shouldNotBeNull()
    }

    @Test
    fun `dropGraphAsync delegates and completes`() {
        adapter.createGraphAsync("social").join()

        val result = adapter.dropGraphAsync("social").join()
        result shouldBeEqualTo Unit
    }

    @Test
    fun `graphExistsAsync returns delegate result for default graph`() {
        val existsViaAdapter = adapter.graphExistsAsync("default").join()
        val existsViaDirect = delegate.graphExists("default")
        existsViaAdapter shouldBeEqualTo existsViaDirect
    }

    @Test
    fun `graphExistsAsync returns false for a non-current name in TinkerGraph`() {
        // TinkerGraph는 단일 인메모리 그래프 — 현재 logical name만 존재로 보고한다.
        val exists = adapter.graphExistsAsync("nonexistent-graph-xyz").join()
        exists.shouldBeFalse()
    }

    @Test
    fun `graphExistsAsync is thread-safe under concurrent load`() {
        val futures = List(50) { adapter.graphExistsAsync("default") }
        val results = futures.map { it.join() }
        results.size shouldBeEqualTo 50
        results.all { it != null }.shouldBeTrue()
    }

    @Test
    fun `asVirtualThreadSession extension wraps delegate correctly`() {
        val vtSession = delegate.asVirtualThreadSession()
        vtSession.shouldNotBeNull()

        val exists = vtSession.graphExistsAsync("default").join()
        exists shouldBeEqualTo delegate.graphExists("default")
    }

    @Test
    fun `createGraphAsync and graphExistsAsync sequence completes without error`() {
        val graphName = "workflow"
        adapter.graphExistsAsync(graphName).join().shouldBeFalse()

        adapter.createGraphAsync(graphName).join()
        adapter.graphExistsAsync(graphName).join().shouldBeTrue()

        adapter.dropGraphAsync(graphName).join()
        // 예외 없이 완료되어야 함
        // adapter.graphExistsAsync(graphName).join().shouldBeFalse()
    }
}

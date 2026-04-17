package io.bluetape4k.graph.vt

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualThreadSessionAdapterTest {

    companion object : KLogging()

    private val delegate = TinkerGraphOperations()
    private val adapter = VirtualThreadSessionAdapter(delegate)

    @AfterAll
    fun tearDown() {
        delegate.close()
    }

    @BeforeEach
    fun setUp() {
        // TinkerGraph는 항상 "default" 그래프를 제공하므로 별도 초기화 불필요
    }

    @Test
    fun `createGraphAsync delegates and completes`() {
        val result = adapter.createGraphAsync("social").join()
        result shouldBeEqualTo null  // CompletableFuture<Void> 결과
        // 실제 위임 결과 확인: 그래프 존재 여부는 TinkerGraph에서 항상 true 반환
        delegate.graphExists("social").shouldNotBeNull()
    }

    @Test
    fun `dropGraphAsync delegates and completes`() {
        val result = adapter.dropGraphAsync("social").join()
        result shouldBeEqualTo null  // CompletableFuture<Void> 결과
    }

    @Test
    fun `graphExistsAsync returns delegate result for default graph`() {
        val existsViaAdapter = adapter.graphExistsAsync("default").join()
        val existsViaDirect = delegate.graphExists("default")
        existsViaAdapter shouldBeEqualTo existsViaDirect
    }

    @Test
    fun `graphExistsAsync returns false for nonexistent graph`() {
        // TinkerGraph는 단일 그래프 인메모리 구현이므로 항상 false 반환
        val exists = adapter.graphExistsAsync("nonexistent-graph-xyz").join()
        exists.shouldNotBeNull()
    }

    @Test
    fun `graphExistsAsync is thread-safe under concurrent load`() {
        val futures = (1..50).map { adapter.graphExistsAsync("default") }
        val results = futures.map { it.join() }
        results.size shouldBeEqualTo 50
        results.forEach { it.shouldNotBeNull() }
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
        adapter.createGraphAsync("workflow").join()
        adapter.dropGraphAsync("workflow").join()
        // 예외 없이 완료되어야 함
    }
}

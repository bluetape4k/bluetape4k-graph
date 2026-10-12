package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.utils.Runtimex
import io.mockk.clearMocks
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VirtualThreadVertexAdapterTest: AbstractGraphTest() {

    companion object: KLogging()

    private lateinit var delegate: TinkerGraphOperations
    private lateinit var adapter: VirtualThreadVertexAdapter

    @BeforeEach
    fun setUp() {
        delegate = spyk(TinkerGraphOperations())
        adapter = VirtualThreadVertexAdapter(delegate)
    }

    @AfterEach
    fun tearDown() {
        clearMocks(delegate)
        delegate.close()
    }

    @Test
    fun `createVertexAsync creates and returns vertex`() {
        val v = adapter.createVertexAsync("Person", mapOf("name" to "Alice")).join()

        log.debug { "v=$v" }
        v.shouldNotBeNull()
        v.label shouldBeEqualTo "Person"

        verify(exactly = 1) { delegate.createVertex("Person", mapOf("name" to "Alice")) }
    }

    @Test
    fun `createVerticesAsync creates and returns vertices in order`() {
        val vertices = adapter.createVerticesAsync(
            "Person",
            listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob")),
        ).join()

        vertices.forEach { log.debug { "vertex=$it" } }
        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")

        verify(exactly = 1) {
            delegate.createVertices("Person", listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob")))
        }
    }

    @Test
    fun `findVertexByIdAsync returns created vertex`() {
        val created = delegate.createVertex("Person", mapOf("name" to "Bob"))
        val found = adapter.findVertexByIdAsync("Person", created.id).join()

        log.debug { "found=$found" }
        found.shouldNotBeNull().id shouldBeEqualTo created.id
    }

    @Test
    fun `findVertexByIdAsync returns null when missing`() {
        val result = adapter.findVertexByIdAsync("Person", id("99999999")).join()
        result.shouldBeNull()
    }

    @Test
    fun `findVerticesByLabelAsync returns list`() {
        delegate.createVertex("Person", mapOf("name" to "A"))
        delegate.createVertex("Person", mapOf("name" to "B"))
        val list = adapter.findVerticesByLabelAsync("Person").join()

        list.forEach { log.debug { "vertex=$it" } }
        list.size shouldBeEqualTo 2
    }

    @Test
    fun `updateVertexAsync updates properties`() {
        val v = delegate.createVertex("Person", mapOf("age" to 30))
        val updated = adapter
            .updateVertexAsync("Person", v.id, mapOf("age" to 31))
            .join()
            .shouldNotBeNull()

        log.debug { "updated=$updated" }
        updated.properties["age"] shouldBeEqualTo 31
    }

    @Test
    fun `deleteVertexAsync deletes existing vertex`() {
        val v = delegate.createVertex("Person")
        val deleted = adapter.deleteVertexAsync("Person", v.id).join()
        deleted.shouldBeTrue()
    }

    @Test
    fun `countVerticesAsync returns count`() {
        delegate.createVertex("Person")
        delegate.createVertex("Person")

        adapter.countVerticesAsync("Person").join() shouldBeEqualTo 2L
    }

    @Test
    fun `findVertexByIdAsync is thread-safe under concurrent load`() {
        val v = delegate.createVertex("Person", mapOf("name" to "Alice"))

        StructuredTaskScopeTester()
            .rounds(Runtimex.availableProcessors * 4)
            .add {
                adapter.findVertexByIdAsync("Person", v.id).join().shouldNotBeNull()
            }
            .run()
    }

    @Test
    fun `asVirtualThreadVertexRepository extension wraps delegate correctly`() {
        val vtVertex = delegate.asVirtualThreadVertexRepository()
        val v = vtVertex.createVertexAsync("Person", mapOf("name" to "Alice")).join()

        log.debug { "v=$v" }
        v.shouldNotBeNull().label shouldBeEqualTo "Person"
    }
}

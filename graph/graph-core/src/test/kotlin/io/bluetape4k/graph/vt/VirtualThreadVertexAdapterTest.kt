package io.bluetape4k.graph.vt

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import io.mockk.spyk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VirtualThreadVertexAdapterTest {

    companion object : KLogging()

    private lateinit var delegate: TinkerGraphOperations
    private lateinit var adapter: VirtualThreadVertexAdapter

    @BeforeEach
    fun setUp() {
        delegate = spyk(TinkerGraphOperations())
        adapter = VirtualThreadVertexAdapter(delegate)
    }

    @AfterEach
    fun tearDown() {
        delegate.close()
    }

    @Test
    fun `createVertexAsync creates and returns vertex`() {
        val v = adapter.createVertexAsync("Person", mapOf("name" to "Alice")).join()
        v.shouldNotBeNull()
        v.label shouldBeEqualTo "Person"
        verify(exactly = 1) { delegate.createVertex("Person", mapOf("name" to "Alice")) }
    }

    @Test
    fun `findVertexByIdAsync returns created vertex`() {
        val created = delegate.createVertex("Person", mapOf("name" to "Bob"))
        val found = adapter.findVertexByIdAsync("Person", created.id).join()
        found.shouldNotBeNull().id shouldBeEqualTo created.id
    }

    @Test
    fun `findVertexByIdAsync returns null when missing`() {
        val result = adapter.findVertexByIdAsync("Person", GraphElementId.of("missing")).join()
        result shouldBeEqualTo null
    }

    @Test
    fun `findVerticesByLabelAsync returns list`() {
        delegate.createVertex("Person", mapOf("name" to "A"))
        delegate.createVertex("Person", mapOf("name" to "B"))
        val list = adapter.findVerticesByLabelAsync("Person").join()
        list.size shouldBeEqualTo 2
    }

    @Test
    fun `updateVertexAsync updates properties`() {
        val v = delegate.createVertex("Person", mapOf("age" to 30))
        val updated = adapter.updateVertexAsync("Person", v.id, mapOf("age" to 31)).join()
        updated.shouldNotBeNull().properties["age"] shouldBeEqualTo 31
    }

    @Test
    fun `deleteVertexAsync deletes existing vertex`() {
        val v = delegate.createVertex("Person")
        val deleted = adapter.deleteVertexAsync("Person", v.id).join()
        deleted shouldBeEqualTo true
    }

    @Test
    fun `countVerticesAsync returns count`() {
        delegate.createVertex("Person")
        delegate.createVertex("Person")
        val count = adapter.countVerticesAsync("Person").join()
        count shouldBeEqualTo 2L
    }

    @Test
    fun `findVertexByIdAsync is thread-safe under concurrent load`() {
        val v = delegate.createVertex("Person", mapOf("name" to "Alice"))
        StructuredTaskScopeTester()
            .rounds(50)
            .add { adapter.findVertexByIdAsync("Person", v.id).join().shouldNotBeNull() }
            .run()
    }

    @Test
    fun `asVirtualThreadVertexRepository extension wraps delegate correctly`() {
        val vtVertex = delegate.asVirtualThreadVertexRepository()
        val v = vtVertex.createVertexAsync("Person", mapOf("name" to "Alice")).join()
        v.shouldNotBeNull()
        v.label shouldBeEqualTo "Person"
    }
}

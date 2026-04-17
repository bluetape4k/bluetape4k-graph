package io.bluetape4k.graph.vt

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

class VirtualThreadEdgeAdapterTest {

    companion object : KLogging()

    private lateinit var delegate: TinkerGraphOperations
    private lateinit var adapter: VirtualThreadEdgeAdapter

    @BeforeEach
    fun setUp() {
        delegate = spyk(TinkerGraphOperations())
        adapter = VirtualThreadEdgeAdapter(delegate)
    }

    @AfterEach
    fun tearDown() {
        delegate.close()
    }

    @Test
    fun `createEdgeAsync creates and returns edge`() {
        val from = delegate.createVertex("Person", mapOf("name" to "Alice"))
        val to = delegate.createVertex("Person", mapOf("name" to "Bob"))
        val edge = adapter.createEdgeAsync(from.id, to.id, "KNOWS", mapOf("since" to 2024)).join()
        edge.shouldNotBeNull()
        edge.label shouldBeEqualTo "KNOWS"
        verify(exactly = 1) { delegate.createEdge(from.id, to.id, "KNOWS", mapOf("since" to 2024)) }
    }

    @Test
    fun `findEdgesByLabelAsync returns edges`() {
        val from = delegate.createVertex("Person")
        val to = delegate.createVertex("Person")
        delegate.createEdge(from.id, to.id, "KNOWS")
        delegate.createEdge(from.id, to.id, "KNOWS")
        val edges = adapter.findEdgesByLabelAsync("KNOWS").join()
        edges.size shouldBeEqualTo 2
    }

    @Test
    fun `deleteEdgeAsync deletes existing edge`() {
        val from = delegate.createVertex("Person")
        val to = delegate.createVertex("Person")
        val edge = delegate.createEdge(from.id, to.id, "KNOWS")
        adapter.deleteEdgeAsync("KNOWS", edge.id).join() shouldBeEqualTo true
    }

    @Test
    fun `findEdgesByLabelAsync is thread-safe under concurrent load`() {
        val from = delegate.createVertex("Person")
        val to = delegate.createVertex("Person")
        delegate.createEdge(from.id, to.id, "KNOWS")
        StructuredTaskScopeTester()
            .rounds(50)
            .add { adapter.findEdgesByLabelAsync("KNOWS").join().shouldNotBeNull() }
            .run()
    }

    @Test
    fun `asVirtualThreadEdge extension wraps delegate correctly`() {
        val from = delegate.createVertex("Person", mapOf("name" to "Alice"))
        val to = delegate.createVertex("Person", mapOf("name" to "Bob"))
        val vtEdge = delegate.asVirtualThreadEdge()
        val edge = vtEdge.createEdgeAsync(from.id, to.id, "KNOWS").join()
        edge.shouldNotBeNull()
        edge.label shouldBeEqualTo "KNOWS"
    }
}

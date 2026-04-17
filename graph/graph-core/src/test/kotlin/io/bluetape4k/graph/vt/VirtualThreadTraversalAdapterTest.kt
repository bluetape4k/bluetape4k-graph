package io.bluetape4k.graph.vt

import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import io.mockk.spyk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VirtualThreadTraversalAdapterTest {

    companion object : KLogging()

    private lateinit var delegate: TinkerGraphOperations
    private lateinit var adapter: VirtualThreadTraversalAdapter

    @BeforeEach
    fun setUp() {
        delegate = spyk(TinkerGraphOperations())
        adapter = VirtualThreadTraversalAdapter(delegate)
    }

    @AfterEach
    fun tearDown() {
        delegate.close()
    }

    // -- neighborsAsync --

    @Test
    fun `neighborsAsync returns empty list when no neighbors exist`() {
        val v = delegate.createVertex("Node", mapOf("name" to "lonely"))
        val result = adapter.neighborsAsync(v.id).join()
        result.shouldNotBeNull()
        result shouldHaveSize 0
        verify(exactly = 1) { delegate.neighbors(v.id, NeighborOptions.Default) }
    }

    @Test
    fun `neighborsAsync returns connected vertices`() {
        val from = delegate.createVertex("Person", mapOf("name" to "Alice"))
        val to = delegate.createVertex("Person", mapOf("name" to "Bob"))
        delegate.createEdge(from.id, to.id, "KNOWS", emptyMap())

        val result = adapter.neighborsAsync(from.id, NeighborOptions(edgeLabel = "KNOWS")).join()
        result.shouldNotBeNull()
        result shouldHaveSize 1
        result.first().id shouldBeEqualTo to.id
        verify(exactly = 1) { delegate.neighbors(from.id, NeighborOptions(edgeLabel = "KNOWS")) }
    }

    @Test
    fun `neighborsAsync is thread-safe under concurrent load`() {
        val v = delegate.createVertex("Node", mapOf("name" to "A"))
        StructuredTaskScopeTester()
            .rounds(50)
            .add { adapter.neighborsAsync(v.id).join().shouldNotBeNull() }
            .run()
    }

    // -- shortestPathAsync --

    @Test
    fun `shortestPathAsync returns null when no path exists`() {
        val isolated = delegate.createVertex("Node", mapOf("name" to "X"))
        val other = delegate.createVertex("Node", mapOf("name" to "Y"))

        val result = adapter.shortestPathAsync(isolated.id, other.id).join()
        result.shouldBeNull()
        verify(exactly = 1) { delegate.shortestPath(isolated.id, other.id, PathOptions.Default) }
    }

    @Test
    fun `shortestPathAsync returns path when connected`() {
        val alice = delegate.createVertex("Person", mapOf("name" to "Alice"))
        val bob = delegate.createVertex("Person", mapOf("name" to "Bob"))
        val carol = delegate.createVertex("Person", mapOf("name" to "Carol"))
        delegate.createEdge(alice.id, bob.id, "KNOWS", emptyMap())
        delegate.createEdge(bob.id, carol.id, "KNOWS", emptyMap())

        val result = adapter.shortestPathAsync(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS")).join()
        result.shouldNotBeNull()
        result.vertices.shouldNotBeEmpty()
        verify(exactly = 1) { delegate.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS")) }
    }

    @Test
    fun `shortestPathAsync is thread-safe under concurrent load`() {
        val from = delegate.createVertex("Node", mapOf("name" to "S"))
        val to = delegate.createVertex("Node", mapOf("name" to "T"))
        delegate.createEdge(from.id, to.id, "LINK", emptyMap())

        StructuredTaskScopeTester()
            .rounds(50)
            .add { adapter.shortestPathAsync(from.id, to.id, PathOptions(edgeLabel = "LINK")).join() }
            .run()
    }

    // -- allPathsAsync --

    @Test
    fun `allPathsAsync returns empty list when no paths exist`() {
        val a = delegate.createVertex("Node", mapOf("name" to "A"))
        val b = delegate.createVertex("Node", mapOf("name" to "B"))

        val result = adapter.allPathsAsync(a.id, b.id).join()
        result.shouldNotBeNull()
        result shouldHaveSize 0
        verify(exactly = 1) { delegate.allPaths(a.id, b.id, PathOptions.Default) }
    }

    @Test
    fun `allPathsAsync returns all paths when multiple routes exist`() {
        val a = delegate.createVertex("Node", mapOf("name" to "A"))
        val b = delegate.createVertex("Node", mapOf("name" to "B"))
        val c = delegate.createVertex("Node", mapOf("name" to "C"))
        delegate.createEdge(a.id, b.id, "LINK", emptyMap())
        delegate.createEdge(a.id, c.id, "LINK", emptyMap())
        delegate.createEdge(c.id, b.id, "LINK", emptyMap())

        val result = adapter.allPathsAsync(a.id, b.id, PathOptions(edgeLabel = "LINK")).join()
        result.shouldNotBeNull()
        // At least the direct path exists
        result.shouldNotBeEmpty()
        verify(exactly = 1) { delegate.allPaths(a.id, b.id, PathOptions(edgeLabel = "LINK")) }
    }

    @Test
    fun `allPathsAsync is thread-safe under concurrent load`() {
        val v = delegate.createVertex("Node", mapOf("name" to "A"))
        StructuredTaskScopeTester()
            .rounds(50)
            .add { adapter.allPathsAsync(v.id, v.id).join().shouldNotBeNull() }
            .run()
    }

    // -- extension function --

    @Test
    fun `asVirtualThreadTraversal extension wraps delegate correctly`() {
        val vtOps = delegate.asVirtualThreadTraversal()
        val v = delegate.createVertex("Node", mapOf("name" to "Z"))
        vtOps.neighborsAsync(v.id).join().shouldNotBeNull()
    }
}

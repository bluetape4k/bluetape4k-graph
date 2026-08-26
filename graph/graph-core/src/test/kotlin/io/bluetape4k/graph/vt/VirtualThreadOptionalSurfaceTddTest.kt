package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphCapabilities
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphTransactionScope
import io.bluetape4k.graph.repository.GraphTransactionalOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.repository.capabilities
import io.bluetape4k.graph.repository.createIndexAsync
import io.bluetape4k.graph.repository.mergeVertexAsync
import io.bluetape4k.graph.repository.transactionAsync
import io.bluetape4k.graph.schema.schemaManager
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Test

class VirtualThreadOptionalSurfaceTddTest {

    @Test
    fun `focused adapters advertise their own optional capability`() {
        val delegate = TinkerGraphOperations()
        try {
            val merge = (delegate as GraphMergeOperations).asVirtualThreadMerge()
            val schema = delegate.schemaManager().asVirtualThreadSchema()
            val transaction = (delegate as GraphTransactionalOperations).asVirtualThreadTransactional()
            val chunked = delegate.asVirtualThreadChunked()

            GraphCapabilities.from(merge)
                .supports(GraphCapability.MERGE).shouldBeTrue()
            GraphCapabilities.from(schema)
                .supports(GraphCapability.SCHEMA).shouldBeTrue()
            GraphCapabilities.from(transaction)
                .supports(GraphCapability.TRANSACTION).shouldBeTrue()
            val chunkCapabilities = GraphCapabilities.from(chunked)
            chunkCapabilities.supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
            chunkCapabilities.supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
        } finally {
            delegate.close()
        }
    }

    @Test
    fun `supported facade exposes optional capabilities and keeps transaction affinity`() {
        val delegate = TinkerGraphOperations()
        try {
            val adapter = VirtualThreadOperationsAdapter(delegate)
            val capabilities = adapter.capabilities()

            capabilities.supports(GraphCapability.MERGE).shouldBeTrue()
            capabilities.supports(GraphCapability.SCHEMA).shouldBeTrue()
            capabilities.supports(GraphCapability.TRANSACTION).shouldBeTrue()
            capabilities.supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
            capabilities.supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
            capabilities.supports(GraphCapability.BOUNDED_CHUNKED_READ).shouldBeTrue()
            capabilities.supports(GraphCapability.BOUNDED_CHUNKED_EXPORT).shouldBeTrue()
            adapter.delegateCapabilities() shouldBeEqualTo delegate.capabilities()

            val alice = adapter.createVertexAsync("Person", mapOf("email" to "alice@example.com")).join()
            val merged = adapter.mergeVertexAsync(
                "Person",
                matchProperties = mapOf("email" to "alice@example.com"),
                setProperties = mapOf("name" to "Alice"),
            ).join()
            merged.id shouldBeEqualTo alice.id

            val bob = adapter.createVertexAsync("Person", mapOf("name" to "Bob")).join()
            adapter.createEdgeAsync(alice.id, bob.id, "KNOWS").join()

            adapter.createIndexAsync("Person", "email").join()
            adapter.listIndexesAsync().join().single().label shouldBeEqualTo "Person"
            val chunks = adapter.findVerticesByLabelChunkedAsync("Person", chunkSize = 1).join()
            chunks.map(List<GraphVertex>::size) shouldBeEqualTo listOf(1, 1)
            val edgeChunks = adapter.findEdgesByLabelChunkedAsync("KNOWS", chunkSize = 1).join()
            edgeChunks.flatten().single().label shouldBeEqualTo "KNOWS"

            val transactionThread = adapter.transactionAsync { Thread.currentThread() }.join()
            transactionThread.isVirtual().shouldBeTrue()
        } finally {
            delegate.close()
        }
    }

    @Test
    fun `unsupported delegate does not advertise optional backend capabilities`() {
        val delegate = TinkerGraphOperations()
        try {
            val adapter = VirtualThreadOperationsAdapter(GraphOperationsDecorator(delegate))
            val capabilities = adapter.capabilities()

            capabilities.supports(GraphCapability.MERGE).shouldBeFalse()
            capabilities.supports(GraphCapability.SCHEMA).shouldBeFalse()
            capabilities.supports(GraphCapability.TRANSACTION).shouldBeFalse()
            capabilities.supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
            capabilities.supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
            capabilities.supports(GraphCapability.BOUNDED_CHUNKED_READ).shouldBeFalse()
            capabilities.supports(GraphCapability.BOUNDED_CHUNKED_EXPORT).shouldBeFalse()

            val facade: GraphVirtualThreadOperations = adapter
            assertFailsWith<CompletionException> {
                facade.mergeVertexAsync("Person", mapOf("email" to "unsupported@example.com")).join()
            }.cause shouldBeInstanceOf UnsupportedOperationException::class
            assertFailsWith<CompletionException> {
                facade.createIndexAsync("Person", "email").join()
            }.cause shouldBeInstanceOf UnsupportedOperationException::class
            assertFailsWith<CompletionException> {
                facade.transactionAsync { Unit }.join()
            }.cause shouldBeInstanceOf UnsupportedOperationException::class
        } finally {
            delegate.close()
        }
    }

    @Test
    fun `exception from synchronous delegate is preserved by future`() {
        val expected = IllegalStateException("merge failed")
        val adapter = VirtualThreadMergeAdapter(FailingMerge(expected))

        val failure = assertFailsWith<CompletionException> {
            adapter.mergeVertexAsync("Person", mapOf("email" to "failure@example.com")).join()
        }

        failure.cause shouldBeEqualTo expected
    }

    @Test
    fun `future cancellation and timeout remain observable without closing delegate`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter = VirtualThreadMergeAdapter(BlockingMerge(started, release))

        val cancelled = adapter.mergeVertexAsync("Person", mapOf("email" to "cancel@example.com"))
        started.await(2, TimeUnit.SECONDS).shouldBeTrue()
        cancelled.cancel(true).shouldBeTrue()
        cancelled.isCancelled.shouldBeTrue()

        val timeoutStarted = CountDownLatch(1)
        val timeoutRelease = CountDownLatch(1)
        val timeoutAdapter = VirtualThreadMergeAdapter(BlockingMerge(timeoutStarted, timeoutRelease))
        val timed = timeoutAdapter
            .mergeVertexAsync("Person", mapOf("email" to "timeout@example.com"))
            .orTimeout(Duration.ofMillis(100).toMillis(), TimeUnit.MILLISECONDS)
        timeoutStarted.await(2, TimeUnit.SECONDS).shouldBeTrue()
        val timeoutFailure = assertFailsWith<CompletionException> { timed.join() }
        timeoutFailure.cause shouldBeInstanceOf TimeoutException::class

        release.countDown()
        timeoutRelease.countDown()
    }

    private class GraphOperationsDecorator(
        delegate: GraphOperations,
    ) : GraphOperations by delegate

    private class FailingMerge(
        private val failure: Throwable,
    ) : GraphMergeOperations {
        override fun mergeVertex(
            label: String,
            matchProperties: Map<String, Any?>,
            setProperties: Map<String, Any?>,
        ): GraphVertex = throw failure

        override fun mergeEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            matchProperties: Map<String, Any?>,
            setProperties: Map<String, Any?>,
        ): GraphEdge = throw failure
    }

    private class BlockingMerge(
        private val started: CountDownLatch,
        private val release: CountDownLatch,
    ) : GraphMergeOperations {
        override fun mergeVertex(
            label: String,
            matchProperties: Map<String, Any?>,
            setProperties: Map<String, Any?>,
        ): GraphVertex {
            started.countDown()
            release.await()
            return GraphVertex(GraphElementId.of("v-1"), label, matchProperties + setProperties)
        }

        override fun mergeEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            matchProperties: Map<String, Any?>,
            setProperties: Map<String, Any?>,
        ): GraphEdge {
            started.countDown()
            release.await()
            return GraphEdge(GraphElementId.of("e-1"), label, fromId, toId, matchProperties + setProperties)
        }
    }
}

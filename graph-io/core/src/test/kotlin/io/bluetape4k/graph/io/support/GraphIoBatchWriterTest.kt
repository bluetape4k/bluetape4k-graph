package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.coroutines.support.log
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.testsupport.FakeGraphOperations
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.edgeOf
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.graph.model.vertexOf
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GraphIoBatchWriterTest {

    companion object: KLogging()

    private val operations = mockk<GraphSuspendOperations>()

    @BeforeEach
    fun beforeEach() {
        clearMocks(operations)
    }

    @Test
    fun `sync writer rejects non-positive batch size`() {
        assertFailsWith<IllegalArgumentException> {
            GraphIoBatchWriter(FakeGraphOperations(), batchSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphIoBatchWriter(FakeGraphOperations(), batchSize = -1)
        }
    }

    @Test
    fun `suspend writer rejects non-positive batch size`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            SuspendGraphIoBatchWriter(operations, batchSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SuspendGraphIoBatchWriter(operations, batchSize = -1)
        }
    }

    @Test
    fun `vertex writer flushes by label and maps returned ids in input order`() {
        val operations = object: FakeGraphOperations() {
            val calls = mutableListOf<List<Map<String, Any?>>>()
            var sequence = 0

            override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> {
                label shouldBeEqualTo "Person"
                calls += propertiesList
                return propertiesList.map { properties ->
                    sequence++
                    vertexOf("v$sequence", label, properties)
                        .also { log.debug { "vertex=$it" } }
                }
            }
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val writer = GraphIoBatchWriter(operations, batchSize = 2)

        listOf("a", "b", "c").forEach { id ->
            idMap.putFirstOrFail(id, id.toGraphElementId())
            writer.addVertex(id, "Person", mapOf("name" to id.uppercase()), idMap)
        }

        operations.calls shouldHaveSize 1
        operations.calls.single().map { it["name"] } shouldBeEqualTo listOf("A", "B")
        idMap.resolve("a") shouldBeEqualTo "v1".toGraphElementId()
        idMap.resolve("b") shouldBeEqualTo "v2".toGraphElementId()
        idMap.resolve("c") shouldBeEqualTo "c".toGraphElementId()

        writer.flushVertices(idMap) shouldBeEqualTo 1
        operations.calls shouldHaveSize 2
        operations.calls.last().map { it["name"] } shouldBeEqualTo listOf("C")
        idMap.resolve("c") shouldBeEqualTo "v3".toGraphElementId()
    }

    @Test
    fun `edge writer flushes by label and batch size`() {
        val operations = object: FakeGraphOperations() {
            val calls = mutableListOf<List<BatchEdge>>()
            var sequence = 0

            override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> {
                label shouldBeEqualTo "KNOWS"
                calls += edges
                return edges.map { edge ->
                    sequence++
                    edgeOf("e$sequence", label, edge.fromId, edge.toId, edge.properties)
                        .also { log.debug { "edge=$it" } }
                }
            }
        }
        val writer = GraphIoBatchWriter(operations, batchSize = 2)
        val a = "a".toGraphElementId()
        val b = "b".toGraphElementId()
        val c = "c".toGraphElementId()

        writer.addEdge("KNOWS", a, b, "rank" to 1)
        writer.addEdge("KNOWS", b, c, "rank" to 2)
        writer.addEdge("KNOWS", c, a, "rank" to 3)

        operations.calls shouldHaveSize 1
        operations.calls.single().map { it.properties["rank"] } shouldBeEqualTo listOf(1, 2)

        writer.flushEdges() shouldBeEqualTo 1
        operations.calls shouldHaveSize 2
        operations.calls.last().map { it.properties["rank"] } shouldBeEqualTo listOf(3)
    }

    @Test
    fun `flush on empty buffers is a no-op`() {
        val writer = GraphIoBatchWriter(FakeGraphOperations(), batchSize = 2)
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)

        writer.flushVertices(idMap) shouldBeEqualTo 0
        writer.flushEdges() shouldBeEqualTo 0
        idMap.resolve("missing").shouldBeNull()
    }

    @Test
    fun `suspend vertex writer flushes by label and maps returned ids in input order`() = runTest {
        val calls = mutableListOf<List<Map<String, Any?>>>()
        var sequence = 0

        coEvery {
            operations.createVertices("Person", any())
        } coAnswers {
            val rows = secondArg<List<Map<String, Any?>>>()
            calls += rows
            rows.map { properties ->
                sequence++
                vertexOf("sv$sequence", "Person", properties)
            }.onEach { log.debug { "vertex=$it" } }
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 2)

        listOf("a", "b", "c").forEach { id ->
            idMap.putFirstOrFail(id, id.toGraphElementId())
            writer.addVertex(id, "Person", mapOf("name" to id.uppercase()), idMap)
        }

        calls shouldHaveSize 1
        calls.single().map { it["name"] } shouldBeEqualTo listOf("A", "B")
        idMap.resolve("a") shouldBeEqualTo "sv1".toGraphElementId()
        idMap.resolve("b") shouldBeEqualTo "sv2".toGraphElementId()
        idMap.resolve("c") shouldBeEqualTo "c".toGraphElementId()

        writer.flushVertices(idMap) shouldBeEqualTo 1
        calls shouldHaveSize 2
        calls.last().map { it["name"] } shouldBeEqualTo listOf("C")
        idMap.resolve("c") shouldBeEqualTo "sv3".toGraphElementId()

        coVerify(exactly = 2) { operations.createVertices("Person", any()) }
    }

    @Test
    fun `suspend edge writer flushes by label and batch size`() = runTest {
        val calls = mutableListOf<List<BatchEdge>>()
        var sequence = 0
        coEvery {
            operations.createEdges("KNOWS", any())
        } coAnswers {
            val edges = secondArg<List<BatchEdge>>()
            calls += edges
            edges.map { edge ->
                sequence++
                edgeOf("se$sequence", "KNOWS", edge.fromId, edge.toId, edge.properties)
            }.onEach { log.debug { "edge=$it" } }
        }

        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 2)
        val a = "a".toGraphElementId()
        val b = "b".toGraphElementId()
        val c = "c".toGraphElementId()

        writer.addEdge("KNOWS", a, b, "rank" to 1)
        writer.addEdge("KNOWS", b, c, "rank" to 2)
        writer.addEdge("KNOWS", c, a, "rank" to 3)

        calls shouldHaveSize 1
        calls.single().map { it.properties["rank"] } shouldBeEqualTo listOf(1, 2)

        writer.flushEdges() shouldBeEqualTo 1
        calls shouldHaveSize 2
        calls.last().map { it.properties["rank"] } shouldBeEqualTo listOf(3)

        coVerify(exactly = 2) { operations.createEdges("KNOWS", any()) }
    }

    @Test
    fun `suspend flush on empty buffers is a no-op`() = runTest {
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 2)
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)

        writer.flushVertices(idMap) shouldBeEqualTo 0
        writer.flushEdges() shouldBeEqualTo 0

        idMap.resolve("missing").shouldBeNull()

        coVerify(exactly = 0) { operations.createVertices(any(), any()) }
        coVerify(exactly = 0) { operations.createEdges(any(), any()) }
    }

    @Test
    fun `suspend vertex cancellation is rethrown without failure callback`() = runTest {
        val cancellation = CancellationException("vertex-cancelled")
        val failures = mutableListOf<Pair<String, Throwable>>()

        coEvery {
            operations.createVertices("Person", any())
        } throws cancellation

        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += (boundary to cause)
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        idMap.putFirstOrFail("v1", "v1".toGraphElementId())

        val thrown = assertFailsWith<CancellationException> {
            writer.addVertex("v1", "Person", emptyMap(), idMap)
        }

        log.debug { "thrown=$thrown" }
        thrown shouldBeSameInstanceAs cancellation
        failures.shouldBeEmpty()
    }

    @Test
    fun `suspend edge cancellation is rethrown without failure callback`() = runTest {
        val cancellation = CancellationException("edge-cancelled")
        val failures = mutableListOf<Pair<String, Throwable>>()

        coEvery {
            operations.createEdges("KNOWS", any())
        } throws cancellation

        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += (boundary to cause)
        }

        val thrown = assertFailsWith<CancellationException> {
            writer.addEdge("KNOWS", "a".toGraphElementId(), "b".toGraphElementId())
        }

        thrown shouldBeSameInstanceAs cancellation
        failures.shouldBeEmpty()
    }

    @Test
    fun `suspend vertex writer propagates cancellation from the active coroutine job`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val failures = mutableListOf<Pair<String, Throwable>>()

        coEvery {
            operations.createVertices("Person", any())
        } coAnswers {
            entered.complete(Unit)
            awaitCancellation()
        }

        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        idMap.putFirstOrFail("v1", "v1".toGraphElementId())
        val cancellation = CancellationException("vertex-job-cancelled")

        val job = async {
            writer.addVertex("v1", "Person", emptyMap(), idMap)
        }.log("Job")

        entered.await()
        job.cancel(cancellation)
        val thrown = assertFailsWith<CancellationException> { job.await() }

        thrown.message shouldBeEqualTo cancellation.message
        failures.shouldBeEmpty()
    }

    @Test
    fun `suspend edge writer propagates cancellation from the active coroutine job`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val failures = mutableListOf<Pair<String, Throwable>>()

        coEvery {
            operations.createEdges("KNOWS", any())
        } coAnswers {
            entered.complete(Unit)
            awaitCancellation()
        }
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }
        val cancellation = CancellationException("edge-job-cancelled")
        val job = async {
            writer.addEdge("KNOWS", "a".toGraphElementId(), "b".toGraphElementId())
        }.log("Job")

        entered.await()
        job.cancel(cancellation)
        val thrown = assertFailsWith<CancellationException> {
            job.await()
        }

        thrown.message shouldBeEqualTo cancellation.message
        failures.shouldBeEmpty()
    }

    @Test
    fun `suspend backend failure still invokes vertex failure callback`() = runTest {
        val failure = IllegalStateException("backend-failure")
        val failures = mutableListOf<Pair<String, Throwable>>()

        coEvery { operations.createVertices("Person", any()) } throws failure

        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        idMap.putFirstOrFail("v1", "v1".toGraphElementId())

        val thrown = assertFailsWith<IllegalStateException> {
            writer.addVertex("v1", "Person", emptyMap(), idMap)
        }

        thrown shouldBeSameInstanceAs failure
        failures shouldHaveSize 1
        failures.single().first shouldBeEqualTo "VERTICES"
        failures.single().second shouldBeSameInstanceAs failure
    }

    @Test
    fun `suspend edge row mismatch still invokes edge failure callback`() = runTest {
        val failures = mutableListOf<Pair<String, Throwable>>()

        coEvery { operations.createEdges("KNOWS", any()) } returns emptyList()

        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }

        val thrown = assertFailsWith<IllegalArgumentException> {
            writer.addEdge("KNOWS", "a".toGraphElementId(), "b".toGraphElementId())
        }

        failures.shouldHaveSize(1)
        failures.single().first shouldBeEqualTo "EDGES"
        failures.single().second shouldBeSameInstanceAs thrown
    }
}

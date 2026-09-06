package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.testsupport.FakeGraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import org.junit.jupiter.api.Test
import kotlinx.coroutines.CancellationException

class GraphIoBatchWriterTest {

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
    fun `suspend writer rejects non-positive batch size`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        assertFailsWith<IllegalArgumentException> {
            SuspendGraphIoBatchWriter(operations, batchSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SuspendGraphIoBatchWriter(operations, batchSize = -1)
        }
    }

    @Test
    fun `vertex writer flushes by label and maps returned ids in input order`() {
        val operations = object : FakeGraphOperations() {
            val calls = mutableListOf<List<Map<String, Any?>>>()
            var sequence = 0

            override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> {
                label shouldBeEqualTo "Person"
                calls += propertiesList
                return propertiesList.map { properties ->
                    sequence++
                    GraphVertex(GraphElementId.of("v$sequence"), label, properties)
                }
            }
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val writer = GraphIoBatchWriter(operations, batchSize = 2)

        listOf("a", "b", "c").forEach { id ->
            idMap.putFirstOrFail(id, GraphElementId.of(id))
            writer.addVertex(id, "Person", mapOf("name" to id.uppercase()), idMap)
        }

        operations.calls.shouldHaveSize(1)
        operations.calls.single().map { it["name"] } shouldBeEqualTo listOf("A", "B")
        idMap.resolve("a") shouldBeEqualTo GraphElementId.of("v1")
        idMap.resolve("b") shouldBeEqualTo GraphElementId.of("v2")
        idMap.resolve("c") shouldBeEqualTo GraphElementId.of("c")

        writer.flushVertices(idMap) shouldBeEqualTo 1
        operations.calls.shouldHaveSize(2)
        operations.calls.last().map { it["name"] } shouldBeEqualTo listOf("C")
        idMap.resolve("c") shouldBeEqualTo GraphElementId.of("v3")
    }

    @Test
    fun `edge writer flushes by label and batch size`() {
        val operations = object : FakeGraphOperations() {
            val calls = mutableListOf<List<BatchEdge>>()
            var sequence = 0

            override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> {
                label shouldBeEqualTo "KNOWS"
                calls += edges
                return edges.map { edge ->
                    sequence++
                    GraphEdge(GraphElementId.of("e$sequence"), label, edge.fromId, edge.toId, edge.properties)
                }
            }
        }
        val writer = GraphIoBatchWriter(operations, batchSize = 2)
        val a = GraphElementId.of("a")
        val b = GraphElementId.of("b")
        val c = GraphElementId.of("c")

        writer.addEdge("KNOWS", a, b, mapOf("rank" to 1))
        writer.addEdge("KNOWS", b, c, mapOf("rank" to 2))
        writer.addEdge("KNOWS", c, a, mapOf("rank" to 3))

        operations.calls.shouldHaveSize(1)
        operations.calls.single().map { it.properties["rank"] } shouldBeEqualTo listOf(1, 2)

        writer.flushEdges() shouldBeEqualTo 1
        operations.calls.shouldHaveSize(2)
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
    fun `suspend vertex writer flushes by label and maps returned ids in input order`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val calls = mutableListOf<List<Map<String, Any?>>>()
        var sequence = 0
        coEvery { operations.createVertices("Person", any()) } coAnswers {
            val rows = secondArg<List<Map<String, Any?>>>()
            calls += rows
            rows.map { properties ->
                sequence++
                GraphVertex(GraphElementId.of("sv$sequence"), "Person", properties)
            }
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 2)

        listOf("a", "b", "c").forEach { id ->
            idMap.putFirstOrFail(id, GraphElementId.of(id))
            writer.addVertex(id, "Person", mapOf("name" to id.uppercase()), idMap)
        }

        calls.shouldHaveSize(1)
        calls.single().map { it["name"] } shouldBeEqualTo listOf("A", "B")
        idMap.resolve("a") shouldBeEqualTo GraphElementId.of("sv1")
        idMap.resolve("b") shouldBeEqualTo GraphElementId.of("sv2")
        idMap.resolve("c") shouldBeEqualTo GraphElementId.of("c")

        writer.flushVertices(idMap) shouldBeEqualTo 1
        calls.shouldHaveSize(2)
        calls.last().map { it["name"] } shouldBeEqualTo listOf("C")
        idMap.resolve("c") shouldBeEqualTo GraphElementId.of("sv3")
        coVerify(exactly = 2) { operations.createVertices("Person", any()) }
    }

    @Test
    fun `suspend edge writer flushes by label and batch size`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val calls = mutableListOf<List<BatchEdge>>()
        var sequence = 0
        coEvery { operations.createEdges("KNOWS", any()) } coAnswers {
            val edges = secondArg<List<BatchEdge>>()
            calls += edges
            edges.map { edge ->
                sequence++
                GraphEdge(GraphElementId.of("se$sequence"), "KNOWS", edge.fromId, edge.toId, edge.properties)
            }
        }
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 2)
        val a = GraphElementId.of("a")
        val b = GraphElementId.of("b")
        val c = GraphElementId.of("c")

        writer.addEdge("KNOWS", a, b, mapOf("rank" to 1))
        writer.addEdge("KNOWS", b, c, mapOf("rank" to 2))
        writer.addEdge("KNOWS", c, a, mapOf("rank" to 3))

        calls.shouldHaveSize(1)
        calls.single().map { it.properties["rank"] } shouldBeEqualTo listOf(1, 2)

        writer.flushEdges() shouldBeEqualTo 1
        calls.shouldHaveSize(2)
        calls.last().map { it.properties["rank"] } shouldBeEqualTo listOf(3)
        coVerify(exactly = 2) { operations.createEdges("KNOWS", any()) }
    }

    @Test
    fun `suspend flush on empty buffers is a no-op`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 2)
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)

        writer.flushVertices(idMap) shouldBeEqualTo 0
        writer.flushEdges() shouldBeEqualTo 0
        idMap.resolve("missing").shouldBeNull()
        coVerify(exactly = 0) { operations.createVertices(any(), any()) }
        coVerify(exactly = 0) { operations.createEdges(any(), any()) }
    }

    @Test
    fun `suspend vertex cancellation is rethrown without failure callback`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val cancellation = CancellationException("vertex-cancelled")
        val failures = mutableListOf<Pair<String, Throwable>>()
        coEvery { operations.createVertices("Person", any()) } throws cancellation
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        idMap.putFirstOrFail("v1", GraphElementId.of("v1"))

        val thrown = assertFailsWith<CancellationException> {
            writer.addVertex("v1", "Person", emptyMap(), idMap)
        }

        thrown shouldBeSameInstanceAs cancellation
        failures.shouldBeEmpty()
    }

    @Test
    fun `suspend edge cancellation is rethrown without failure callback`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val cancellation = CancellationException("edge-cancelled")
        val failures = mutableListOf<Pair<String, Throwable>>()
        coEvery { operations.createEdges("KNOWS", any()) } throws cancellation
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }

        val thrown = assertFailsWith<CancellationException> {
            writer.addEdge("KNOWS", GraphElementId.of("a"), GraphElementId.of("b"), emptyMap())
        }

        thrown shouldBeSameInstanceAs cancellation
        failures.shouldBeEmpty()
    }

    @Test
    fun `suspend backend failure still invokes vertex failure callback`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val failure = IllegalStateException("backend-failure")
        val failures = mutableListOf<Pair<String, Throwable>>()
        coEvery { operations.createVertices("Person", any()) } throws failure
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }
        val idMap = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        idMap.putFirstOrFail("v1", GraphElementId.of("v1"))

        val thrown = assertFailsWith<IllegalStateException> {
            writer.addVertex("v1", "Person", emptyMap(), idMap)
        }

        thrown shouldBeSameInstanceAs failure
        failures.shouldHaveSize(1)
        failures.single().first shouldBeEqualTo "VERTICES"
        failures.single().second shouldBeSameInstanceAs failure
    }

    @Test
    fun `suspend edge row mismatch still invokes edge failure callback`() = runSuspendIO {
        val operations = mockk<GraphSuspendOperations>()
        val failures = mutableListOf<Pair<String, Throwable>>()
        coEvery { operations.createEdges("KNOWS", any()) } returns emptyList()
        val writer = SuspendGraphIoBatchWriter(operations, batchSize = 1) { boundary, cause ->
            failures += boundary to cause
        }

        val thrown = assertFailsWith<IllegalArgumentException> {
            writer.addEdge("KNOWS", GraphElementId.of("a"), GraphElementId.of("b"), emptyMap())
        }

        failures.shouldHaveSize(1)
        failures.single().first shouldBeEqualTo "EDGES"
        failures.single().second shouldBeSameInstanceAs thrown
    }
}

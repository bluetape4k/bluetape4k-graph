package io.bluetape4k.graph.repository

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class GraphBatchOperationsTest {

    companion object {
        private fun unsupported(): Nothing = error("not used by batch default tests")
    }

    @Test
    fun `default createVertices preserves order and calls createVertex per row`() {
        val repo = RecordingVertexRepository()
        val rows = listOf(
            mapOf("name" to "Alice"),
            mapOf("name" to "Bob"),
        )

        val vertices = repo.createVertices("Person", rows)

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")
        repo.createdProperties shouldBeEqualTo rows
    }

    @Test
    fun `default createVertices returns empty list without backend call`() {
        val repo = RecordingVertexRepository()

        repo.createVertices("Person", emptyList()) shouldBeEqualTo emptyList()
        repo.createdProperties shouldBeEqualTo emptyList()
    }

    @Test
    fun `default createEdges preserves order and calls createEdge per row`() {
        val repo = RecordingEdgeRepository()
        val from = GraphElementId.of("v1")
        val to = GraphElementId.of("v2")
        val rows = listOf(
            BatchEdge(from, to, mapOf("since" to 2024)),
            BatchEdge(to, from, mapOf("since" to 2025)),
        )

        val edges = repo.createEdges("KNOWS", rows)

        edges.map { it.properties["since"] } shouldBeEqualTo listOf(2024, 2025)
        repo.createdEdges shouldBeEqualTo rows
    }

    @Test
    fun `default createEdges returns empty list without backend call`() {
        val repo = RecordingEdgeRepository()

        repo.createEdges("KNOWS", emptyList()) shouldBeEqualTo emptyList()
        repo.createdEdges shouldBeEqualTo emptyList()
    }

    @Test
    fun `default findVerticesByLabelChunked splits list fallback`() {
        val repo = ListingVertexRepository(
            (1..5).map { index ->
                GraphVertex(GraphElementId.of("v$index"), "Person", mapOf("index" to index))
            }
        )

        val chunks = repo.findVerticesByLabelChunked("Person", chunkSize = 2).toList()

        chunks.map { it.size } shouldBeEqualTo listOf(2, 2, 1)
        chunks.flatten().map { it.id.value } shouldBeEqualTo listOf("v1", "v2", "v3", "v4", "v5")
    }

    @Test
    fun `default vertex chunk fallback materializes the label before yielding`() {
        val repo = ListingVertexRepository(
            (1..3).map { index ->
                GraphVertex(GraphElementId.of("v$index"), "Person")
            },
        )

        val chunks = repo.findVerticesByLabelChunked("Person", chunkSize = 2)

        repo.lookupCount shouldBeEqualTo 1
        chunks.toList().map { it.size } shouldBeEqualTo listOf(2, 1)
    }

    @Test
    fun `default findEdgesByLabelChunked splits list fallback`() {
        val repo = ListingEdgeRepository(
            (1..3).map { index ->
                GraphEdge(
                    GraphElementId.of("e$index"),
                    "KNOWS",
                    GraphElementId.of("v$index"),
                    GraphElementId.of("v${index + 1}"),
                )
            }
        )

        val chunks = repo.findEdgesByLabelChunked("KNOWS", chunkSize = 2).toList()

        chunks.map { it.size } shouldBeEqualTo listOf(2, 1)
        chunks.flatten().map { it.id.value } shouldBeEqualTo listOf("e1", "e2", "e3")
    }

    @Test
    fun `default chunk fallback advertises API chunking without bounded execution`() {
        val repo = ListingVertexRepository(emptyList())
        val capabilities = GraphCapabilities.from(repo)

        capabilities.supports(GraphCapability.CHUNKED_READ).shouldBeTrue()
        capabilities.supports(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()
        capabilities.supports(GraphCapability.BOUNDED_CHUNKED_READ).shouldBeFalse()
        capabilities.supports(GraphCapability.BOUNDED_CHUNKED_EXPORT).shouldBeFalse()
        capabilities.constraints(GraphCapability.CHUNKED_READ)
            .contains("api-chunking-only").shouldBeTrue()
        capabilities.constraints(GraphCapability.CHUNKED_EXPORT)
            .contains("api-chunking-only").shouldBeTrue()
    }

    @Test
    fun `default chunked lookup rejects non-positive chunk size`() {
        val repo = ListingVertexRepository(emptyList())

        val ex = assertFailsWith<IllegalArgumentException> {
            repo.findVerticesByLabelChunked("Person", chunkSize = 0).toList()
        }

        ex.message shouldContain "chunkSize"
    }

    @Test
    fun `suspend default createVertices preserves order`() = runSuspendIO {
        val repo = RecordingSuspendVertexRepository()
        val rows = listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob"))

        val vertices = repo.createVertices("Person", rows)

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")
        repo.createdProperties shouldBeEqualTo rows
    }

    @Test
    fun `suspend default createEdges preserves order`() = runSuspendIO {
        val repo = RecordingSuspendEdgeRepository()
        val rows = listOf(
            BatchEdge(GraphElementId.of("v1"), GraphElementId.of("v2"), mapOf("since" to 2024)),
            BatchEdge(GraphElementId.of("v2"), GraphElementId.of("v1"), mapOf("since" to 2025)),
        )

        val edges = repo.createEdges("KNOWS", rows)

        edges.map { it.properties["since"] } shouldBeEqualTo listOf(2024, 2025)
        repo.createdEdges shouldBeEqualTo rows
    }

    @Test
    fun `suspend default findVerticesByLabelChunked splits Flow fallback`() = runSuspendIO {
        val repo = ListingSuspendVertexRepository(
            (1..5).map { index ->
                GraphVertex(GraphElementId.of("v$index"), "Person", mapOf("index" to index))
            }
        )

        val chunks = repo.findVerticesByLabelChunked("Person", chunkSize = 2).toList()

        chunks.map { it.size } shouldBeEqualTo listOf(2, 2, 1)
        chunks.flatten().map { it.id.value } shouldBeEqualTo listOf("v1", "v2", "v3", "v4", "v5")
    }

    @Test
    fun `suspend default vertex chunk fallback materializes the label before emitting`() = runSuspendIO {
        val repo = ListingSuspendVertexRepository(
            (1..3).map { index ->
                GraphVertex(GraphElementId.of("v$index"), "Person")
            },
        )

        val chunks = repo.findVerticesByLabelChunked("Person", chunkSize = 2)

        repo.lookupCount shouldBeEqualTo 1
        chunks.toList().map { it.size } shouldBeEqualTo listOf(2, 1)
    }

    @Test
    fun `suspend default findEdgesByLabelChunked splits Flow fallback`() = runSuspendIO {
        val repo = ListingSuspendEdgeRepository(
            (1..3).map { index ->
                GraphEdge(
                    GraphElementId.of("e$index"),
                    "KNOWS",
                    GraphElementId.of("v$index"),
                    GraphElementId.of("v${index + 1}"),
                )
            }
        )

        val chunks = repo.findEdgesByLabelChunked("KNOWS", chunkSize = 2).toList()

        chunks.map { it.size } shouldBeEqualTo listOf(2, 1)
        chunks.flatten().map { it.id.value } shouldBeEqualTo listOf("e1", "e2", "e3")
    }

    @Test
    fun `batch validation rejects unsafe label`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphBatchValidation.validateVertexBatch("Person) DETACH DELETE n", listOf(emptyMap()))
        }

        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `batch validation rejects unsafe property key`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphBatchValidation.validateVertexBatch("Person", listOf(mapOf("name) DELETE n" to "Alice")))
        }

        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `blank edge endpoint cannot be constructed before batch validation`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphElementId("")
        }

        ex.message shouldContain "value"
    }

    @Test
    fun `GraphOperations facade resolves batch methods`() {
        val ops: GraphOperations = TinkerGraphOperations()
        ops.use {
            val vertices = it.createVertices("Person", listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob")))
            vertices.map { vertex -> vertex.label } shouldBeEqualTo listOf("Person", "Person")
            it.createEdges("KNOWS", listOf(BatchEdge(vertices[0].id, vertices[1].id))).single().label shouldBeEqualTo
                    "KNOWS"
        }
    }

    @Test
    fun `GraphSuspendOperations facade resolves batch methods`() = runSuspendIO {
        val ops: GraphSuspendOperations = TinkerGraphSuspendOperations()
        ops.use {
            val vertices = it.createVertices("Person", listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob")))
            vertices.map { vertex -> vertex.label } shouldBeEqualTo listOf("Person", "Person")
            it.createEdges("KNOWS", listOf(BatchEdge(vertices[0].id, vertices[1].id))).single().label shouldBeEqualTo
                    "KNOWS"
        }
    }

    private class RecordingVertexRepository : GraphVertexRepository {
        val createdProperties = mutableListOf<Map<String, Any?>>()

        override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
            createdProperties += properties
            return GraphVertex(GraphElementId.of("v${createdProperties.size}"), label, properties)
        }

        override fun findVertexById(label: String, id: GraphElementId): GraphVertex? = unsupported()
        override fun findVertexById(id: GraphElementId): GraphVertex? = unsupported()
        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> = unsupported()
        override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
            unsupported()
        override fun deleteVertex(label: String, id: GraphElementId): Boolean = unsupported()
        override fun countVertices(label: String): Long = unsupported()
    }

    private class RecordingEdgeRepository : GraphEdgeRepository {
        val createdEdges = mutableListOf<BatchEdge>()

        override fun createEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            properties: Map<String, Any?>,
        ): GraphEdge {
            createdEdges += BatchEdge(fromId, toId, properties)
            return GraphEdge(GraphElementId.of("e${createdEdges.size}"), label, fromId, toId, properties)
        }

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> = unsupported()
        override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> = unsupported()
        override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> = unsupported()
        override fun deleteEdge(label: String, id: GraphElementId): Boolean = unsupported()
    }

    private class ListingVertexRepository(
        private val vertices: List<GraphVertex>,
    ) : GraphVertexRepository {
        var lookupCount: Int = 0

        override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex = unsupported()
        override fun findVertexById(label: String, id: GraphElementId): GraphVertex? = unsupported()
        override fun findVertexById(id: GraphElementId): GraphVertex? = unsupported()
        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
            lookupCount++
            return vertices
        }
        override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
            unsupported()
        override fun deleteVertex(label: String, id: GraphElementId): Boolean = unsupported()
        override fun countVertices(label: String): Long = vertices.size.toLong()
    }

    private class ListingEdgeRepository(
        private val edges: List<GraphEdge>,
    ) : GraphEdgeRepository {
        override fun createEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            properties: Map<String, Any?>,
        ): GraphEdge = unsupported()
        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> = edges
        override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> = unsupported()
        override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> = unsupported()
        override fun deleteEdge(label: String, id: GraphElementId): Boolean = unsupported()
    }

    private class RecordingSuspendVertexRepository : GraphSuspendVertexRepository {
        val createdProperties = mutableListOf<Map<String, Any?>>()

        override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
            createdProperties += properties
            return GraphVertex(GraphElementId.of("v${createdProperties.size}"), label, properties)
        }

        override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? = unsupported()
        override suspend fun findVertexById(id: GraphElementId): GraphVertex? = unsupported()
        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> = unsupported()
        override suspend fun updateVertex(
            label: String,
            id: GraphElementId,
            properties: Map<String, Any?>,
        ): GraphVertex? = unsupported()
        override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean = unsupported()
        override suspend fun countVertices(label: String): Long = unsupported()
    }

    private class RecordingSuspendEdgeRepository : GraphSuspendEdgeRepository {
        val createdEdges = mutableListOf<BatchEdge>()

        override suspend fun createEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            properties: Map<String, Any?>,
        ): GraphEdge {
            createdEdges += BatchEdge(fromId, toId, properties)
            return GraphEdge(GraphElementId.of("e${createdEdges.size}"), label, fromId, toId, properties)
        }

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> = unsupported()
        override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> = unsupported()
        override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> = unsupported()
        override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean = unsupported()
    }

    private class ListingSuspendVertexRepository(
        private val vertices: List<GraphVertex>,
    ) : GraphSuspendVertexRepository {
        var lookupCount: Int = 0

        override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex = unsupported()
        override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? = unsupported()
        override suspend fun findVertexById(id: GraphElementId): GraphVertex? = unsupported()
        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
            lookupCount++
            return vertices.asFlow()
        }
        override suspend fun updateVertex(
            label: String,
            id: GraphElementId,
            properties: Map<String, Any?>,
        ): GraphVertex? = unsupported()
        override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean = unsupported()
        override suspend fun countVertices(label: String): Long = vertices.size.toLong()
    }

    private class ListingSuspendEdgeRepository(
        private val edges: List<GraphEdge>,
    ) : GraphSuspendEdgeRepository {
        override suspend fun createEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            properties: Map<String, Any?>,
        ): GraphEdge = unsupported()
        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> =
            edges.asFlow()
        override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> = unsupported()
        override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): Flow<GraphEdge> = unsupported()
        override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean = unsupported()
    }

}

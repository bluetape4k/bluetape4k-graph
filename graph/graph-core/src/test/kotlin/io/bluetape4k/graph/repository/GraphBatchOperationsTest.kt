package io.bluetape4k.graph.repository

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.Flow
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
    fun `batch validation rejects blank edge endpoint`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphBatchValidation.validateEdgeBatch(
                "KNOWS",
                listOf(BatchEdge(GraphElementId(""), GraphElementId.of("v2"))),
            )
        }

        ex.message shouldContain "fromId.value"
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

}

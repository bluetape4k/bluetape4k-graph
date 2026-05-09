package io.bluetape4k.graph.repository

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.TraversalVisit
import org.junit.jupiter.api.Test

class GraphMergeOperationsTest {

    @Test
    fun `mergeVertex fails clearly when backend does not support merge operations`() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            UnsupportedGraphOperations.mergeVertex("Person", mapOf("email" to "a@example.com"))
        }

        ex.message shouldContain "does not support graph merge operations"
    }

    @Test
    fun `merge extensions delegate to backend capability`() {
        val vertex = GraphVertex(GraphElementId.of("v1"), "Person", mapOf("email" to "a@example.com"))
        val edge = GraphEdge(GraphElementId.of("e1"), "KNOWS", GraphElementId.of("v1"), GraphElementId.of("v2"), emptyMap())
        val ops: GraphOperations = MergeAwareGraphOperations(vertex, edge)

        ops.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "a@example.com"),
            setProperties = mapOf("name" to "Alice"),
        ) shouldBeEqualTo vertex

        ops.mergeEdge(
            fromId = GraphElementId.of("v1"),
            toId = GraphElementId.of("v2"),
            label = "KNOWS",
        ) shouldBeEqualTo edge
    }

    @Test
    fun `vertex validation rejects empty match properties`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphMergeValidation.validateVertex("Person", emptyMap(), emptyMap())
        }

        ex.message shouldContain "must not be empty"
    }

    @Test
    fun `merge validation rejects null match property values`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphMergeValidation.validateVertex("Person", mapOf("email" to null), emptyMap())
        }

        ex.message shouldContain "must not contain null"
    }

    @Test
    fun `merge validation rejects unsafe property names`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphMergeValidation.validateVertex("Person", mapOf("email) DELETE n" to "a@example.com"), emptyMap())
        }

        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `merge validation rejects set properties overwriting match keys`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphMergeValidation.validateVertex(
                label = "Person",
                matchProperties = mapOf("email" to "a@example.com"),
                setProperties = mapOf("email" to "b@example.com"),
            )
        }

        ex.message shouldContain "must not overwrite"
    }

    @Test
    fun `edge validation allows empty match properties`() {
        val properties = GraphMergeValidation.validateEdge(
            fromId = GraphElementId.of("v1"),
            toId = GraphElementId.of("v2"),
            label = "KNOWS",
            matchProperties = emptyMap(),
            setProperties = mapOf("since" to 2024),
        )

        properties.matchProperties shouldBeEqualTo emptyMap()
        properties.setProperties shouldBeEqualTo mapOf("since" to 2024)
    }

    private class MergeAwareGraphOperations(
        private val vertex: GraphVertex,
        private val edge: GraphEdge,
    ): GraphOperations by UnsupportedGraphOperations, GraphMergeOperations {

        override fun mergeVertex(
            label: String,
            matchProperties: Map<String, Any?>,
            setProperties: Map<String, Any?>,
        ): GraphVertex = vertex

        override fun mergeEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            matchProperties: Map<String, Any?>,
            setProperties: Map<String, Any?>,
        ): GraphEdge = edge
    }

    private object UnsupportedGraphOperations: GraphOperations {
        override fun createGraph(name: String) = Unit
        override fun dropGraph(name: String) = Unit
        override fun graphExists(name: String): Boolean = true
        override fun close() = Unit

        override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex = unsupported()
        override fun findVertexById(label: String, id: GraphElementId): GraphVertex? = unsupported()
        override fun findVertexById(id: GraphElementId): GraphVertex? = unsupported()
        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> = unsupported()
        override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
            unsupported()
        override fun deleteVertex(label: String, id: GraphElementId): Boolean = unsupported()
        override fun countVertices(label: String): Long = unsupported()
        override fun createEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            properties: Map<String, Any?>,
        ): GraphEdge = unsupported()
        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> = unsupported()
        override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> = unsupported()
        override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> = unsupported()
        override fun deleteEdge(label: String, id: GraphElementId): Boolean = unsupported()

        override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> = unsupported()
        override fun shortestPath(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): GraphPath? =
            unsupported()
        override fun allPaths(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): List<GraphPath> =
            unsupported()
        override fun aStarPath(
            fromId: GraphElementId,
            toId: GraphElementId,
            options: PathOptions,
            heuristic: (GraphVertex) -> Double,
        ): GraphPath? = unsupported()
        override fun pageRank(options: PageRankOptions): List<PageRankScore> = unsupported()
        override fun degreeCentrality(vertexId: GraphElementId, options: DegreeOptions): DegreeResult = unsupported()
        override fun connectedComponents(options: ComponentOptions): List<GraphComponent> = unsupported()
        override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> = unsupported()
        override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> = unsupported()
        override fun detectCycles(options: CycleOptions): List<GraphCycle> = unsupported()

        private fun unsupported(): Nothing =
            error("merge extension should fail before calling repository methods")
    }
}

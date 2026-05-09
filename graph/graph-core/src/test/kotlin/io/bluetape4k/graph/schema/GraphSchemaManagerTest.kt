package io.bluetape4k.graph.schema

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.ComponentOptions
import io.bluetape4k.graph.model.CycleOptions
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.DegreeResult
import io.bluetape4k.graph.model.GraphComponent
import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphConstraintType
import io.bluetape4k.graph.model.GraphCycle
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.model.PageRankScore
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.TraversalVisit
import io.bluetape4k.graph.repository.GraphOperations
import org.junit.jupiter.api.Test

class GraphSchemaManagerTest {

    @Test
    fun `schemaManager fails clearly when backend does not support schema management`() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            UnsupportedGraphOperations.schemaManager()
        }

        ex.message shouldContain "does not support graph schema management"
    }

    @Test
    fun `schemaManager returns backend capability manager`() {
        val manager = CapturingSchemaManager()
        val ops = SchemaAwareGraphOperations(manager)

        ops.schemaManager() shouldBeEqualTo manager
    }

    @Test
    fun `schema name helper validates unsafe identifiers`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GraphSchemaNames.indexName("Person) MATCH (n", "email")
        }

        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `schema DSL overloads use label and property names`() {
        val manager = CapturingSchemaManager()

        manager.createIndex(PersonLabel, PersonLabel.email)
        manager.createUniqueConstraint(PersonLabel, PersonLabel.email)
        manager.dropIndex(PersonLabel, PersonLabel.email)

        manager.calls shouldBeEqualTo listOf(
            "createIndex:Person:email",
            "createUniqueConstraint:Person:email",
            "dropIndex:Person:email",
        )
    }

    private object PersonLabel: VertexLabel("Person") {
        val email = string("email")
    }

    private class CapturingSchemaManager: GraphSchemaManager {
        val calls = mutableListOf<String>()

        override fun createIndex(label: String, property: String) {
            calls += "createIndex:$label:$property"
        }

        override fun createUniqueConstraint(label: String, property: String) {
            calls += "createUniqueConstraint:$label:$property"
        }

        override fun dropIndex(label: String, property: String) {
            calls += "dropIndex:$label:$property"
        }

        override fun listIndexes(): List<GraphIndex> = emptyList()

        override fun listConstraints(): List<GraphConstraint> =
            listOf(GraphConstraint("bt4k_uc_Person_email", "Person", "email", GraphConstraintType.UNIQUE))
    }

    private class SchemaAwareGraphOperations(
        private val manager: GraphSchemaManager,
    ): GraphOperations by UnsupportedGraphOperations, GraphSchemaManagementOperations {
        override fun schemaManager(): GraphSchemaManager = manager
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
            error("schemaManager extension should fail before repository methods are called")
    }
}

package io.bluetape4k.graph.repository

import io.bluetape4k.assertions.shouldBeInstanceOf
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

class GraphTransactionExtensionsTest {

    @Test
    fun `transaction fails clearly when backend does not support transactions`() {
        val ex = org.junit.jupiter.api.assertThrows<UnsupportedOperationException> {
            UnsupportedGraphOperations.transaction {
                createVertex("Person")
            }
        }

        ex shouldBeInstanceOf UnsupportedOperationException::class
        ex.message shouldContain "does not support graph transactions"
    }

    private object UnsupportedGraphOperations: GraphOperations {
        override fun createGraph(name: String) = Unit
        override fun dropGraph(name: String) = Unit
        override fun graphExists(name: String): Boolean = true
        override fun close() = Unit

        override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
            unsupported()

        override fun findVertexById(label: String, id: GraphElementId): GraphVertex? =
            unsupported()

        override fun findVertexById(id: GraphElementId): GraphVertex? =
            unsupported()

        override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> =
            unsupported()

        override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
            unsupported()

        override fun deleteVertex(label: String, id: GraphElementId): Boolean =
            unsupported()

        override fun countVertices(label: String): Long =
            unsupported()

        override fun createEdge(
            fromId: GraphElementId,
            toId: GraphElementId,
            label: String,
            properties: Map<String, Any?>,
        ): GraphEdge =
            unsupported()

        override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> =
            unsupported()

        override fun findEdgesByStartId(startId: GraphElementId, edgeLabel: String?): List<GraphEdge> =
            unsupported()

        override fun findEdgesByEndId(endId: GraphElementId, edgeLabel: String?): List<GraphEdge> =
            unsupported()

        override fun deleteEdge(label: String, id: GraphElementId): Boolean =
            unsupported()

        override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> =
            unsupported()

        override fun shortestPath(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): GraphPath? =
            unsupported()

        override fun allPaths(fromId: GraphElementId, toId: GraphElementId, options: PathOptions): List<GraphPath> =
            unsupported()

        override fun aStarPath(
            fromId: GraphElementId,
            toId: GraphElementId,
            options: PathOptions,
            heuristic: (GraphVertex) -> Double,
        ): GraphPath? =
            unsupported()

        override fun pageRank(options: PageRankOptions): List<PageRankScore> =
            unsupported()

        override fun degreeCentrality(vertexId: GraphElementId, options: DegreeOptions): DegreeResult =
            unsupported()

        override fun connectedComponents(options: ComponentOptions): List<GraphComponent> =
            unsupported()

        override fun bfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> =
            unsupported()

        override fun dfs(startId: GraphElementId, options: BfsDfsOptions): List<TraversalVisit> =
            unsupported()

        override fun detectCycles(options: CycleOptions): List<GraphCycle> =
            unsupported()

        private fun unsupported(): Nothing =
            error("transaction extension should fail before calling repository methods")
    }
}

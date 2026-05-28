package io.bluetape4k.graph.benchmark.abuser

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphOperations
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

class AgeAbuserDetectionEngine(
    private val graphName: String,
    private val dataSource: DataSource,
): AbuserDetectionEngine {

    override val implementationName: String = "age-exposed"

    private val ops: GraphOperations by lazy {
        Database.connect(dataSource)
        AgeGraphOperations(graphName)
    }

    private var expectedAbusiveAccountIds: Set<String> = emptySet()

    override fun reset() {
        runCatching { ops.dropGraph(graphName) }
        ops.createGraph(graphName)
    }

    override fun load(fixture: AbuserDetectionFixture) {
        expectedAbusiveAccountIds = fixture.expectedAbusiveAccountIds

        val vertices = ops.createVertices(
            ACCOUNT_LABEL,
            fixture.accounts.map { account ->
                mapOf(
                    ACCOUNT_ID_PROPERTY to account.accountId,
                    "segment" to account.segment,
                    "knownAbusive" to account.knownAbusive,
                    "expectedAbusive" to account.expectedAbusive,
                )
            },
        )
        val vertexIds = fixture.accounts.zip(vertices).associate { (account, vertex) -> account.accountId to vertex.id }

        ops.createEdges(
            EDGE_LABEL,
            fixture.edges.map { edge ->
                BatchEdge(
                    fromId = vertexIds.getValue(edge.fromAccountId),
                    toId = vertexIds.getValue(edge.toAccountId),
                    properties = mapOf(
                        "kind" to edge.kind.name,
                        "weight" to edge.weight,
                    ),
                )
            },
        )
    }

    override fun detect(): AbuserDetectionResult {
        val allAccounts = ops.findVerticesByLabel(ACCOUNT_LABEL)
        val knownVertexIds = allAccounts.asSequence()
            .filter { it.properties["knownAbusive"] == true }
            .map { it.id }
            .toSet()
        val knownAccountIds = allAccounts.asSequence()
            .filter { it.id in knownVertexIds }
            .mapNotNull { it.properties[ACCOUNT_ID_PROPERTY] as? String }
            .toSet()

        val accountIdByVertexId = allAccounts.associate { vertex ->
            vertex.id to (vertex.properties[ACCOUNT_ID_PROPERTY] as String)
        }
        val predicted = linkedSetOf<String>()
        var frontier: Set<GraphElementId> = knownVertexIds

        repeat(DETECTION_DEPTH) {
            val next = linkedSetOf<GraphElementId>()
            frontier.forEach { vertexId ->
                ops.findEdgesByStartId(vertexId, EDGE_LABEL).forEach { edge ->
                    val accountId = accountIdByVertexId[edge.endId]
                    if (accountId != null && accountId !in knownAccountIds) {
                        predicted += accountId
                    }
                    next += edge.endId
                }
            }
            frontier = next
        }

        return AbuserDetectionResult(implementationName, predicted, expectedAbusiveAccountIds)
    }

    override fun close() {
        runCatching { ops.dropGraph(graphName) }
        ops.close()
    }

    private companion object {
        const val ACCOUNT_LABEL = "AbuserAccount"
        const val EDGE_LABEL = "ABUSE_LINK"
        const val ACCOUNT_ID_PROPERTY = "accountId"
        const val DETECTION_DEPTH = 2
    }
}

package io.bluetape4k.graph.benchmark.abuser

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.graph.age.sql.AgeTypeParser
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.repository.GraphOperations
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

class AgeAbuserDetectionEngine(
    private val graphName: String,
    private val dataSource: DataSource,
): AbuserDetectionEngine {

    override val implementationName: String = "age-cypher"

    private val ops: GraphOperations by lazy {
        Database.connect(dataSource)
        AgeGraphOperations(graphName)
    }

    private var expectedAbusiveAccountIds: Set<String> = emptySet()
    private var scenario: AbuserDetectionScenario = AbuserDetectionScenario.SHARED

    override fun reset() {
        runCatching { ops.dropGraph(graphName) }
        ops.createGraph(graphName)
    }

    override fun load(fixture: AbuserDetectionFixture) {
        expectedAbusiveAccountIds = fixture.expectedAbusiveAccountIds
        scenario = fixture.scenario

        val vertices = ops.createVertices(
            ACCOUNT_LABEL,
            fixture.accounts.map { account ->
                mapOf(
                    ACCOUNT_ID_PROPERTY to account.accountId,
                    "segment" to account.segment,
                    "knownAbusive" to account.knownAbusive,
                    "expectedAbusive" to account.expectedAbusive,
                    "riskScore" to account.riskScore,
                    "accountAgeHours" to account.accountAgeHours,
                    "sharedDeviceCluster" to account.sharedDeviceCluster,
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
                        "amount" to edge.amount,
                        "createdAtMinute" to edge.createdAtMinute,
                    ),
                )
            },
        )
    }

    override fun detect(): AbuserDetectionResult {
        val upstreamByCandidate = dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val upstreamByCandidate = linkedMapOf<String, MutableSet<String>>()
                for (depth in 1..scenario.hopLimit) {
                    statement.executeQuery(detectCandidatesSql(depth)).use { rs ->
                        while (rs.next()) {
                            val source = AgeTypeParser.parseVertex(rs.getString("source"))
                            val candidate = AgeTypeParser.parseVertex(rs.getString("candidate"))
                            val sourceId = source.properties[ACCOUNT_ID_PROPERTY] as String
                            val candidateId = candidate.properties[ACCOUNT_ID_PROPERTY] as String
                            upstreamByCandidate.getOrPut(candidateId) { linkedSetOf() } += sourceId
                        }
                    }
                }
                upstreamByCandidate
            }
        }
        val predicted = upstreamByCandidate.asSequence()
            .filter { (_, upstream) -> upstream.size >= scenario.minDistinctUpstream }
            .map { (candidate, _) -> candidate }
            .toSet()

        return AbuserDetectionResult(implementationName, predicted, expectedAbusiveAccountIds)
    }

    private fun detectCandidatesSql(depth: Int): String {
        val relationshipChain = (1..depth).joinToString("") { index ->
            "-[e$index:$EDGE_LABEL]->(n$index:$ACCOUNT_LABEL)"
        }
        val candidateAlias = "n$depth"
        val edgeFilters = (1..depth).joinToString("\n              AND ") { index ->
            "e$index.kind = 'TRANSFER' AND e$index.createdAtMinute >= ${scenario.windowStartMinute} " +
                "AND e$index.weight >= ${scenario.riskThreshold}"
        }

        return AgeSql.cypher(
            graphName,
            """
            MATCH (source:$ACCOUNT_LABEL)$relationshipChain
            WHERE source.riskScore >= ${scenario.riskThreshold}
              AND $edgeFilters
            RETURN DISTINCT source, $candidateAlias AS candidate
            """.trimIndent(),
            listOf("source" to "agtype", "candidate" to "agtype"),
        )
    }

    override fun close() {
        runCatching { ops.dropGraph(graphName) }
        ops.close()
    }

    private companion object {
        const val ACCOUNT_LABEL = "AbuserAccount"
        const val EDGE_LABEL = "ABUSE_LINK"
        const val ACCOUNT_ID_PROPERTY = "accountId"
    }
}

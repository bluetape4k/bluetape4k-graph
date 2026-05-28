package io.bluetape4k.graph.benchmark.authz

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.graph.age.sql.AgeTypeParser
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.repository.GraphOperations
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

class AgeAuthzInheritanceEngine(
    private val graphName: String,
    private val dataSource: DataSource,
): AuthzInheritanceEngine {

    override val implementationName: String = "age-cypher"

    private val ops: GraphOperations by lazy {
        Database.connect(dataSource)
        AgeGraphOperations(graphName)
    }
    private var fixture: AuthzInheritanceFixture? = null

    override fun reset() {
        runCatching { ops.dropGraph(graphName) }
        ops.createGraph(graphName)
    }

    override fun load(fixture: AuthzInheritanceFixture) {
        this.fixture = fixture
        val vertices = ops.createVertices(
            NODE_LABEL,
            fixture.nodes.map { node ->
                mapOf(
                    "nodeId" to node.nodeId,
                    "kind" to node.kind.name,
                    "publicApi" to node.publicApi,
                )
            },
        )
        val vertexIds = fixture.nodes.zip(vertices).associate { (node, vertex) -> node.nodeId to vertex.id }
        ops.createEdges(
            EDGE_LABEL,
            fixture.edges.map { edge ->
                BatchEdge(
                    fromId = vertexIds.getValue(edge.fromNodeId),
                    toId = vertexIds.getValue(edge.toNodeId),
                    properties = mapOf(
                        "kind" to edge.kind.name,
                        "effect" to edge.effect.name,
                        "active" to edge.active,
                    ),
                )
            },
        )
    }

    override fun resolve(): AuthzInheritanceResult {
        val loadedFixture = requireNotNull(fixture) { "Authorization fixture is not loaded" }
        val allowed = linkedSetOf<String>()
        val denied = linkedSetOf<String>()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                for (depth in 1..loadedFixture.scenario.hopLimit) {
                    statement.executeQuery(resolveSql(loadedFixture.targetUserId, depth)).use { rs ->
                        while (rs.next()) {
                            val resource = AgeTypeParser.parseVertex(rs.getString("resource"))
                            val resourceId = resource.properties["nodeId"] as String
                            if (rs.getString("effect").trim('"') == AuthzEffect.DENY.name) {
                                denied += resourceId
                            } else {
                                allowed += resourceId
                            }
                        }
                    }
                }
            }
        }
        return AuthzInheritanceResult(implementationName, allowed - denied, loadedFixture.expectedResourceIds)
    }

    private fun resolveSql(targetUserId: String, depth: Int): String {
        val chain = (1..depth).joinToString("") { index ->
            "-[e$index:$EDGE_LABEL]->(n$index:$NODE_LABEL)"
        }
        val resourceAlias = "n$depth"
        val activeFilters = (1..depth).joinToString("\n              AND ") { index -> "e$index.active = true" }
        return AgeSql.cypher(
            graphName,
            """
            MATCH (user:$NODE_LABEL)$chain
            WHERE user.nodeId = '$targetUserId'
              AND $activeFilters
              AND e$depth.kind = '${AuthzEdgeKind.GRANTS.name}'
              AND $resourceAlias.kind = '${AuthzNodeKind.RESOURCE.name}'
              AND $resourceAlias.publicApi = true
            RETURN DISTINCT $resourceAlias AS resource, e$depth.effect AS effect
            """.trimIndent(),
            listOf("resource" to "agtype", "effect" to "agtype"),
        )
    }

    override fun close() {
        runCatching { ops.dropGraph(graphName) }
        ops.close()
    }

    private companion object {
        const val NODE_LABEL = "AuthzNode"
        const val EDGE_LABEL = "AUTHZ_LINK"
    }
}

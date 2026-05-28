package io.bluetape4k.graph.benchmark.authz

import io.bluetape4k.graph.benchmark.abuser.SqlTraversalMode
import javax.sql.DataSource

internal object PostgreSqlAuthzInheritanceSupport {

    fun resolveResources(
        dataSource: DataSource,
        nodesTableName: String,
        edgesTableName: String,
        fixture: AuthzInheritanceFixture,
        mode: SqlTraversalMode,
    ): Set<String> =
        when (mode) {
            SqlTraversalMode.RECURSIVE_CTE -> resolveWithRecursiveCte(dataSource, nodesTableName, edgesTableName, fixture)
            SqlTraversalMode.ITERATIVE -> resolveIteratively(dataSource, nodesTableName, edgesTableName, fixture)
        }

    private fun resolveWithRecursiveCte(
        dataSource: DataSource,
        nodesTableName: String,
        edgesTableName: String,
        fixture: AuthzInheritanceFixture,
    ): Set<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                WITH RECURSIVE reachable(node_id, depth, path) AS (
                    SELECT ?::VARCHAR, 0, ARRAY[?::VARCHAR]
                    UNION ALL
                    SELECT edge.to_node_id, reachable.depth + 1, reachable.path || edge.to_node_id
                    FROM $edgesTableName edge
                    JOIN reachable ON reachable.node_id = edge.from_node_id
                    WHERE edge.active = TRUE
                      AND reachable.depth < ?
                      AND NOT edge.to_node_id = ANY(reachable.path)
                ),
                grants AS (
                    SELECT edge.to_node_id, edge.effect
                    FROM reachable
                    JOIN $edgesTableName edge ON edge.from_node_id = reachable.node_id
                    JOIN $nodesTableName node ON node.node_id = edge.to_node_id
                    WHERE edge.active = TRUE
                      AND edge.kind = 'GRANTS'
                      AND node.kind = 'RESOURCE'
                      AND node.public_api = TRUE
                )
                SELECT to_node_id
                FROM grants
                GROUP BY to_node_id
                HAVING BOOL_OR(effect = 'ALLOW') AND NOT BOOL_OR(effect = 'DENY')
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, fixture.targetUserId)
                statement.setString(2, fixture.targetUserId)
                statement.setInt(3, fixture.scenario.hopLimit)
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) add(rs.getString(1))
                    }
                }
            }
        }

    private fun resolveIteratively(
        dataSource: DataSource,
        nodesTableName: String,
        edgesTableName: String,
        fixture: AuthzInheritanceFixture,
    ): Set<String> =
        dataSource.connection.use { connection ->
            val allowed = linkedSetOf<String>()
            val denied = linkedSetOf<String>()
            val visited = linkedSetOf(fixture.targetUserId)
            var frontier = setOf(fixture.targetUserId)

            repeat(fixture.scenario.hopLimit) {
                if (frontier.isEmpty()) return@repeat
                val next = linkedSetOf<String>()
                connection.prepareStatement(
                    """
                    SELECT edge.to_node_id, edge.kind, edge.effect, node.kind, node.public_api
                    FROM $edgesTableName edge
                    JOIN $nodesTableName node ON node.node_id = edge.to_node_id
                    WHERE edge.active = TRUE
                      AND edge.from_node_id = ANY (?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setArray(1, connection.createArrayOf("varchar", frontier.toTypedArray()))
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            val toNodeId = rs.getString(1)
                            val edgeKind = rs.getString(2)
                            val effect = rs.getString(3)
                            val nodeKind = rs.getString(4)
                            val publicApi = rs.getBoolean(5)
                            if (edgeKind == "GRANTS" && nodeKind == "RESOURCE" && publicApi) {
                                if (effect == "DENY") denied += toNodeId else allowed += toNodeId
                            } else if (visited.add(toNodeId)) {
                                next += toNodeId
                            }
                        }
                    }
                }
                frontier = next
            }
            allowed - denied
        }
}

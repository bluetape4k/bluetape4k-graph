package io.bluetape4k.graph.benchmark.authz

import io.bluetape4k.graph.benchmark.abuser.SqlTraversalMode
import javax.sql.DataSource

class SqlAuthzInheritanceEngine(
    private val dataSource: DataSource,
    private val traversalMode: SqlTraversalMode,
): AuthzInheritanceEngine {

    override val implementationName: String = "postgres-${traversalMode.displayName}"

    private var fixture: AuthzInheritanceFixture? = null

    override fun reset() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS $EDGES_TABLE")
                statement.execute("DROP TABLE IF EXISTS $NODES_TABLE")
                statement.execute(
                    """
                    CREATE TABLE $NODES_TABLE (
                        node_id VARCHAR(32) PRIMARY KEY,
                        kind VARCHAR(16) NOT NULL,
                        public_api BOOLEAN NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE $EDGES_TABLE (
                        id BIGSERIAL PRIMARY KEY,
                        from_node_id VARCHAR(32) NOT NULL REFERENCES $NODES_TABLE(node_id),
                        to_node_id VARCHAR(32) NOT NULL REFERENCES $NODES_TABLE(node_id),
                        kind VARCHAR(24) NOT NULL,
                        effect VARCHAR(8) NOT NULL,
                        active BOOLEAN NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE INDEX idx_authz_edges_from ON $EDGES_TABLE(from_node_id)")
                statement.execute("CREATE INDEX idx_authz_edges_to ON $EDGES_TABLE(to_node_id)")
                statement.execute("CREATE INDEX idx_authz_edges_kind ON $EDGES_TABLE(kind, active)")
            }
        }
    }

    override fun load(fixture: AuthzInheritanceFixture) {
        this.fixture = fixture
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "INSERT INTO $NODES_TABLE(node_id, kind, public_api) VALUES (?, ?, ?)",
                ).use { statement ->
                    fixture.nodes.forEach { node ->
                        statement.setString(1, node.nodeId)
                        statement.setString(2, node.kind.name)
                        statement.setBoolean(3, node.publicApi)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement(
                    "INSERT INTO $EDGES_TABLE(from_node_id, to_node_id, kind, effect, active) VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    fixture.edges.forEach { edge ->
                        statement.setString(1, edge.fromNodeId)
                        statement.setString(2, edge.toNodeId)
                        statement.setString(3, edge.kind.name)
                        statement.setString(4, edge.effect.name)
                        statement.setBoolean(5, edge.active)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun resolve(): AuthzInheritanceResult {
        val loadedFixture = requireNotNull(fixture) { "Authorization fixture is not loaded" }
        val resources = PostgreSqlAuthzInheritanceSupport.resolveResources(
            dataSource = dataSource,
            nodesTableName = NODES_TABLE,
            edgesTableName = EDGES_TABLE,
            fixture = loadedFixture,
            mode = traversalMode,
        )
        return AuthzInheritanceResult(implementationName, resources, loadedFixture.expectedResourceIds)
    }

    override fun close() = Unit

    private companion object {
        const val NODES_TABLE = "authz_nodes"
        const val EDGES_TABLE = "authz_edges"
    }
}

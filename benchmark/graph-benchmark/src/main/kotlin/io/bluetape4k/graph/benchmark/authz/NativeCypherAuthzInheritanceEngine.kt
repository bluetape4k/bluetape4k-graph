package io.bluetape4k.graph.benchmark.authz

import org.neo4j.driver.Driver

class NativeCypherAuthzInheritanceEngine(
    private val driver: Driver,
    override val implementationName: String,
): AuthzInheritanceEngine {

    private var fixture: AuthzInheritanceFixture? = null

    override fun reset() {
        driver.session().use { session ->
            session.run("MATCH (n:$NODE_LABEL) DETACH DELETE n").consume()
            runCatching {
                session.run("CREATE INDEX authz_node_id IF NOT EXISTS FOR (n:$NODE_LABEL) ON (n.nodeId)").consume()
            }
            runCatching {
                session.run("CREATE INDEX authz_node_kind IF NOT EXISTS FOR (n:$NODE_LABEL) ON (n.kind)").consume()
            }
        }
    }

    override fun load(fixture: AuthzInheritanceFixture) {
        this.fixture = fixture
        driver.session().use { session ->
            fixture.nodes.chunked(BATCH_SIZE).forEach { batch ->
                session.run(
                    """
                    UNWIND ${'$'}rows AS row
                    CREATE (n:$NODE_LABEL {
                        nodeId: row.nodeId,
                        kind: row.kind,
                        publicApi: row.publicApi
                    })
                    """.trimIndent(),
                    mapOf(
                        "rows" to batch.map { node ->
                            mapOf(
                                "nodeId" to node.nodeId,
                                "kind" to node.kind.name,
                                "publicApi" to node.publicApi,
                            )
                        },
                    ),
                ).consume()
            }

            fixture.edges.chunked(BATCH_SIZE).forEach { batch ->
                session.run(
                    """
                    UNWIND ${'$'}rows AS row
                    MATCH (from:$NODE_LABEL {nodeId: row.fromNodeId})
                    MATCH (to:$NODE_LABEL {nodeId: row.toNodeId})
                    CREATE (from)-[:$EDGE_LABEL {
                        kind: row.kind,
                        effect: row.effect,
                        active: row.active
                    }]->(to)
                    """.trimIndent(),
                    mapOf(
                        "rows" to batch.map { edge ->
                            mapOf(
                                "fromNodeId" to edge.fromNodeId,
                                "toNodeId" to edge.toNodeId,
                                "kind" to edge.kind.name,
                                "effect" to edge.effect.name,
                                "active" to edge.active,
                            )
                        },
                    ),
                ).consume()
            }
        }
    }

    override fun resolve(): AuthzInheritanceResult {
        val loadedFixture = requireNotNull(fixture) { "Authorization fixture is not loaded" }
        val allowed = linkedSetOf<String>()
        val denied = linkedSetOf<String>()
        driver.session().use { session ->
            session.run(resolveCypher(loadedFixture.scenario.hopLimit), mapOf("userId" to loadedFixture.targetUserId))
                .list()
                .forEach { record ->
                    val resourceId = record["resourceId"].asString()
                    if (record["effect"].asString() == AuthzEffect.DENY.name) {
                        denied += resourceId
                    } else {
                        allowed += resourceId
                    }
                }
        }
        return AuthzInheritanceResult(implementationName, allowed - denied, loadedFixture.expectedResourceIds)
    }

    private fun resolveCypher(hopLimit: Int): String =
        """
        MATCH (user:$NODE_LABEL {nodeId: ${'$'}userId})
        MATCH path = (user)-[:$EDGE_LABEL*$MIN_AUTHZ_DEPTH..$hopLimit]->(resource:$NODE_LABEL {
            kind: '${AuthzNodeKind.RESOURCE.name}',
            publicApi: true
        })
        WITH resource, relationships(path) AS rels, nodes(path) AS pathNodes
        WHERE ALL(rel IN rels WHERE rel.active = true)
          AND last(rels).kind = '${AuthzEdgeKind.GRANTS.name}'
          AND ALL(rel IN rels[0..size(rels) - 1] WHERE rel.kind <> '${AuthzEdgeKind.GRANTS.name}')
          AND ALL(node IN pathNodes[1..size(pathNodes) - 1] WHERE node.kind <> '${AuthzNodeKind.RESOURCE.name}')
        RETURN DISTINCT resource.nodeId AS resourceId, last(rels).effect AS effect
        """.trimIndent()

    override fun close() {
        driver.session().use { session ->
            runCatching { session.run("MATCH (n:$NODE_LABEL) DETACH DELETE n").consume() }
        }
    }

    private companion object {
        const val BATCH_SIZE = 1_000
        const val MIN_AUTHZ_DEPTH = 3
        const val NODE_LABEL = "AuthzNode"
        const val EDGE_LABEL = "AUTHZ_LINK"
    }
}

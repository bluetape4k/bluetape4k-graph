package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Shared [GraphOperations] contract를 통해 domain-shaped graph workload를 비교한다.
 *
 * ## 동작/계약
 * - Fixture generation은 deterministic하며 각 JMH iteration마다 다시 build된다.
 * - Backend matrix는 의도적으로 선별되어 있다. TinkerGraph는 in-memory baseline, Neo4j는
 *   low-risk production default, Memgraph는 low-latency persistent candidate로 둔다.
 * - Workload는 vendor-specific tuned query가 아니라 domain access shape를 모델링한다.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
open class GraphDomainWorkloadBenchmark {

    @Benchmark
    fun socialHighFanOutExpansion(state: GraphDomainWorkloadState): Int =
        state.ops.neighbors(state.socialAnchorId, state.socialOneHopOptions).size

    @Benchmark
    fun socialTwoHopCandidateLookup(state: GraphDomainWorkloadState): Int =
        state.ops.neighbors(state.socialAnchorId, state.socialTwoHopOptions).size

    @Benchmark
    fun iamPermissionReachability(state: GraphDomainWorkloadState): Boolean =
        state.ops.shortestPath(state.iamUserId, state.iamPermissionId, state.iamPathOptions) != null

    @Benchmark
    fun fraudHighDegreeNeighborhood(state: GraphDomainWorkloadState): Int =
        state.ops.neighbors(state.fraudHubId, state.fraudOneHopOptions).size

    @Benchmark
    fun fraudSuspiciousPathExists(state: GraphDomainWorkloadState): Boolean =
        state.ops.shortestPath(state.fraudSourceId, state.fraudTargetId, state.fraudPathOptions) != null

    @Benchmark
    fun codeDependencyTraversal(state: GraphDomainWorkloadState): Int =
        state.ops.neighbors(state.codeServiceId, state.codeTraversalOptions).size

    @Benchmark
    fun codeReverseDependencyLookup(state: GraphDomainWorkloadState): Int =
        state.ops.findEdgesByEndId(state.codeCoreId, GraphDomainWorkloadState.CODE_EDGE_LABEL).size
}

@State(Scope.Benchmark)
open class GraphDomainWorkloadState {

    companion object {
        const val GRAPH_NAME: String = "graph_domain_workload_benchmark"

        const val SOCIAL_LABEL: String = "SocialUser"
        const val SOCIAL_EDGE_LABEL: String = "FOLLOWS"

        const val IAM_USER_LABEL: String = "IamUser"
        const val IAM_GROUP_LABEL: String = "IamGroup"
        const val IAM_ROLE_LABEL: String = "IamRole"
        const val IAM_PERMISSION_LABEL: String = "IamPermission"
        const val IAM_EDGE_LABEL: String = "IAM_LINK"

        const val FRAUD_ACCOUNT_LABEL: String = "FraudAccount"
        const val FRAUD_EDGE_LABEL: String = "TRANSFER"

        const val CODE_COMPONENT_LABEL: String = "CodeComponent"
        const val CODE_EDGE_LABEL: String = "DEPENDS_ON"
    }

    @Param("tinkergraph", "neo4j", "memgraph")
    lateinit var backend: String

    lateinit var ops: GraphOperations

    var socialAnchorId: GraphElementId = GraphElementId("0")
    var iamUserId: GraphElementId = GraphElementId("0")
    var iamPermissionId: GraphElementId = GraphElementId("0")
    var fraudHubId: GraphElementId = GraphElementId("0")
    var fraudSourceId: GraphElementId = GraphElementId("0")
    var fraudTargetId: GraphElementId = GraphElementId("0")
    var codeServiceId: GraphElementId = GraphElementId("0")
    var codeCoreId: GraphElementId = GraphElementId("0")

    val socialOneHopOptions: NeighborOptions = NeighborOptions(edgeLabel = SOCIAL_EDGE_LABEL, maxDepth = 1)
    val socialTwoHopOptions: NeighborOptions = NeighborOptions(edgeLabel = SOCIAL_EDGE_LABEL, maxDepth = 2)
    val iamPathOptions: PathOptions = PathOptions(edgeLabel = IAM_EDGE_LABEL, maxDepth = 4)
    val fraudOneHopOptions: NeighborOptions = NeighborOptions(edgeLabel = FRAUD_EDGE_LABEL, maxDepth = 1)
    val fraudPathOptions: PathOptions = PathOptions(edgeLabel = FRAUD_EDGE_LABEL, maxDepth = 5)
    val codeTraversalOptions: NeighborOptions = NeighborOptions(edgeLabel = CODE_EDGE_LABEL, maxDepth = 3)

    private var neo4jDriver: Driver? = null

    @Setup(Level.Trial)
    fun setupBackend() {
        ops = when (backend) {
            "tinkergraph" -> TinkerGraphOperations()
            "neo4j" -> {
                val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
                neo4jDriver = driver
                Neo4jGraphOperations(driver)
            }
            "memgraph" -> {
                val driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
                neo4jDriver = driver
                MemgraphGraphOperations(driver)
            }
            else -> error("Unsupported domain workload backend: $backend")
        }
    }

    @Setup(Level.Iteration)
    fun setupGraph() {
        runCatching { ops.dropGraph(GRAPH_NAME) }
        runCatching { ops.createGraph(GRAPH_NAME) }

        seedSocialGraph()
        seedIamGraph()
        seedFraudGraph()
        seedCodeGraph()
    }

    @TearDown(Level.Trial)
    fun teardownBackend() {
        runCatching { ops.dropGraph(GRAPH_NAME) }
        runCatching { ops.close() }
        runCatching { neo4jDriver?.close() }
    }

    private fun seedSocialGraph() {
        val users = ops.createVertices(
            SOCIAL_LABEL,
            (0 until 1_200).map { index ->
                mapOf(
                    "name" to "social-user-$index",
                    "tier" to (index % 5).toLong(),
                )
            },
        )
        socialAnchorId = users.first().id

        val firstHop = (1..240).map { index ->
            BatchEdge(socialAnchorId, users[index].id, mapOf("rank" to index.toLong()))
        }
        val secondHop = (1..240).flatMap { firstHopIndex ->
            (1..4).map { offset ->
                val targetIndex = 241 + ((firstHopIndex - 1) * 4 + offset - 1) % (users.size - 241)
                BatchEdge(users[firstHopIndex].id, users[targetIndex].id, mapOf("rank" to targetIndex.toLong()))
            }
        }
        ops.createEdges(SOCIAL_EDGE_LABEL, firstHop + secondHop)
    }

    private fun seedIamGraph() {
        val users = ops.createVertices(IAM_USER_LABEL, rows("iam-user", 120))
        val groups = ops.createVertices(IAM_GROUP_LABEL, rows("iam-group", 48))
        val roles = ops.createVertices(IAM_ROLE_LABEL, rows("iam-role", 24))
        val permissions = ops.createVertices(IAM_PERMISSION_LABEL, rows("iam-permission", 80))

        iamUserId = users.first().id
        iamPermissionId = permissions[17].id

        val membershipEdges = users.mapIndexed { index, user ->
            BatchEdge(user.id, groups[index % groups.size].id, mapOf("rank" to index.toLong()))
        }
        val inheritanceEdges = groups.mapIndexed { index, group ->
            BatchEdge(group.id, roles[index % roles.size].id, mapOf("rank" to index.toLong()))
        }
        val permissionEdges = roles.flatMapIndexed { roleIndex, role ->
            (0 until 4).map { offset ->
                val permission = permissions[(roleIndex * 4 + offset) % permissions.size]
                BatchEdge(role.id, permission.id, mapOf("rank" to (roleIndex * 4L + offset)))
            }
        }
        ops.createEdges(IAM_EDGE_LABEL, membershipEdges + inheritanceEdges + permissionEdges)
    }

    private fun seedFraudGraph() {
        val accounts = ops.createVertices(FRAUD_ACCOUNT_LABEL, rows("fraud-account", 900))
        fraudHubId = accounts.first().id
        fraudSourceId = accounts[1].id
        fraudTargetId = accounts[450].id

        val hubEdges = (1..320).map { index ->
            BatchEdge(fraudHubId, accounts[index].id, mapOf("amount" to (10_000L + index)))
        }
        val pathEdges = listOf(
            BatchEdge(fraudSourceId, accounts[321].id, mapOf("amount" to 8_000L)),
            BatchEdge(accounts[321].id, accounts[322].id, mapOf("amount" to 8_500L)),
            BatchEdge(accounts[322].id, accounts[323].id, mapOf("amount" to 9_000L)),
            BatchEdge(accounts[323].id, fraudTargetId, mapOf("amount" to 9_500L)),
        )
        val backgroundEdges = (451 until accounts.size).map { index ->
            BatchEdge(accounts[index].id, accounts[1 + (index % 320)].id, mapOf("amount" to (1_000L + index)))
        }
        ops.createEdges(FRAUD_EDGE_LABEL, hubEdges + pathEdges + backgroundEdges)
    }

    private fun seedCodeGraph() {
        val components = ops.createVertices(CODE_COMPONENT_LABEL, rows("code-component", 360))
        codeServiceId = components[120].id
        codeCoreId = components.first().id

        val directDependencies = (1 until components.size).map { index ->
            BatchEdge(components[index].id, components[index % 12].id, mapOf("rank" to index.toLong()))
        }
        val layeredDependencies = (120 until 220).flatMap { index ->
            listOf(
                BatchEdge(components[index].id, components[20 + (index % 40)].id, mapOf("rank" to index.toLong())),
                BatchEdge(components[20 + (index % 40)].id, components[index % 12].id, mapOf("rank" to index.toLong())),
            )
        }
        ops.createEdges(CODE_EDGE_LABEL, directDependencies + layeredDependencies)
    }

    private fun rows(prefix: String, count: Int): List<Map<String, Any?>> =
        (0 until count).map { index ->
            mapOf(
                "name" to "$prefix-$index",
                "rank" to index.toLong(),
            )
        }

}

package io.bluetape4k.graph.benchmark

import com.falkordb.impl.api.DriverImpl
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
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
 * Compares graph database backends through the shared [GraphOperations] contract.
 *
 * Container-backed backends use bluetape4k Testcontainers singleton launchers and should be run serially.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class GraphDbComparisonBenchmark {

    @Benchmark
    fun countPersons(state: GraphDbComparisonState): Long =
        state.ops.countVertices(GraphDbComparisonState.PERSON_LABEL)

    @Benchmark
    fun oneHopNeighbors(state: GraphDbComparisonState): Int =
        state.ops.neighbors(state.anchorId, state.neighborOptions).size

    @Benchmark
    fun shortestPath(state: GraphDbComparisonState): Boolean =
        state.ops.shortestPath(state.anchorId, state.targetId, state.pathOptions) != null

    @Benchmark
    fun batchInsertCycle(state: GraphDbComparisonState): Int {
        val vertices = state.ops.createVertices(GraphDbComparisonState.BATCH_LABEL, state.batchVertexRows)
        val edges = vertices.indices.map { index ->
            BatchEdge(
                fromId = vertices[index].id,
                toId = vertices[(index + 1) % vertices.size].id,
                properties = mapOf("rank" to index.toLong()),
            )
        }

        return state.ops.createEdges(GraphDbComparisonState.BATCH_EDGE_LABEL, edges).size
    }
}

@State(Scope.Benchmark)
open class GraphDbComparisonState {

    companion object {
        const val GRAPH_NAME: String = "graph_benchmark"
        const val PERSON_LABEL: String = "Person"
        const val KNOWS_LABEL: String = "KNOWS"
        const val BATCH_LABEL: String = "BatchPerson"
        const val BATCH_EDGE_LABEL: String = "BATCH_KNOWS"
    }

    @Param("tinkergraph", "neo4j", "memgraph", "age", "falkordb")
    lateinit var backend: String

    @Param("small", "medium")
    lateinit var sizeName: String

    lateinit var ops: GraphOperations
    lateinit var batchVertexRows: List<Map<String, Any?>>

    var anchorId: GraphElementId = GraphElementId("0")
    var targetId: GraphElementId = GraphElementId("0")

    val neighborOptions: NeighborOptions = NeighborOptions(edgeLabel = KNOWS_LABEL, maxDepth = 1)
    val pathOptions: PathOptions = PathOptions(edgeLabel = KNOWS_LABEL, maxDepth = 4)

    private var dataSource: HikariDataSource? = null
    private var neo4jDriver: Driver? = null
    private var falkorDriver: com.falkordb.Driver? = null
    private var falkorServer: FalkorDBServer? = null

    @Setup(Level.Trial)
    fun setupBackend() {
        ops = when (backend) {
            "tinkergraph" -> TinkerGraphOperations()
            "neo4j" -> {
                neo4jDriver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
                Neo4jGraphOperations(neo4jDriver!!)
            }
            "memgraph" -> {
                neo4jDriver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
                MemgraphGraphOperations(neo4jDriver!!)
            }
            "age" -> {
                val server = PostgreSQLAgeServer.Launcher.postgresqlAge
                dataSource = HikariDataSource(HikariConfig().apply {
                    jdbcUrl = server.jdbcUrl
                    username = server.username
                    password = server.password
                    driverClassName = "org.postgresql.Driver"
                    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
                    maximumPoolSize = 4
                })
                Database.connect(dataSource!!)
                AgeGraphOperations(GRAPH_NAME)
            }
            "falkordb" -> {
                val server = FalkorDBServer(reuse = true).apply {
                    withEnv("FALKORDB_ARGS", "MAX_QUEUED_QUERIES 25 TIMEOUT 60000 RESULTSET_SIZE 100000")
                    start()
                }
                falkorServer = server
                falkorDriver = DriverImpl(server.host, server.port)
                FalkorDBGraphOperations(falkorDriver!!, GRAPH_NAME)
            }
            else -> error("Unsupported graph benchmark backend: $backend")
        }
    }

    @Setup(Level.Iteration)
    fun setupGraph() {
        runCatching { ops.dropGraph(GRAPH_NAME) }
        runCatching { ops.createGraph(GRAPH_NAME) }

        val scale = when (sizeName) {
            "small" -> 1_000
            "medium" -> 10_000
            else -> 1_000
        }

        val vertices = ops.createVertices(
            PERSON_LABEL,
            (0 until scale).map { index ->
                mapOf("name" to "Person-$index", "rank" to index.toLong())
            },
        )
        anchorId = vertices.first().id
        targetId = vertices.last().id

        val edges = vertices.dropLast(1).mapIndexed { index, vertex ->
            BatchEdge(
                fromId = vertex.id,
                toId = vertices[index + 1].id,
                properties = mapOf("rank" to index.toLong()),
            )
        }
        ops.createEdges(KNOWS_LABEL, edges)

        batchVertexRows = (0 until (scale / 10).coerceAtLeast(100)).map { index ->
            mapOf("name" to "Batch-$index", "rank" to index.toLong())
        }
    }

    @TearDown(Level.Trial)
    fun teardownBackend() {
        runCatching { ops.dropGraph(GRAPH_NAME) }
        runCatching { ops.close() }
        runCatching { neo4jDriver?.close() }
        runCatching { falkorDriver?.close() }
        runCatching { falkorServer?.close() }
        runCatching { dataSource?.close() }
    }
}

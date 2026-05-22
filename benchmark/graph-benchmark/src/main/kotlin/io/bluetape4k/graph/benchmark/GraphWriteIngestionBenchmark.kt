package io.bluetape4k.graph.benchmark

import com.falkordb.impl.api.DriverImpl
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphVertex
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
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Compares sustained write and batch ingestion profiles across graph database backends.
 *
 * ## Behavior / Contract
 * - Container-backed backends use bluetape4k Testcontainers launchers or wrappers and should be run serially.
 * - Each JMH iteration starts from a fresh graph and a deterministic edge seed pool.
 * - Benchmark invocations intentionally keep inserting new batches during the iteration to model sustained writes.
 * - `repeatedMixedBatches` reports one operation as multiple vertex+edge batches; compare it separately from one-batch rows.
 *
 * ```bash
 * ./gradlew :graph-benchmark:mainGraphWriteIngestion10kBenchmark
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
open class GraphWriteIngestionBenchmark {

    @Benchmark
    fun vertexOnlyBatchInsert(state: GraphWriteIngestionState): Int =
        state.ops.createVertices(
            GraphWriteIngestionState.WRITE_VERTEX_LABEL,
            state.vertexRows(state.nextBatchIndex(), state.batchSize),
        ).size

    @Benchmark
    fun edgeOnlyBatchInsert(state: GraphWriteIngestionState): Int =
        state.ops.createEdges(
            GraphWriteIngestionState.WRITE_EDGE_LABEL,
            state.edgeRows(state.nextBatchIndex(), state.batchSize),
        ).size

    @Benchmark
    fun mixedVertexEdgeInsert(state: GraphWriteIngestionState): Int {
        val batchIndex = state.nextBatchIndex()
        val vertices = state.ops.createVertices(
            GraphWriteIngestionState.MIXED_VERTEX_LABEL,
            state.vertexRows(batchIndex, state.batchSize),
        )
        val edges = state.cycleEdges(vertices, batchIndex)

        return vertices.size + state.ops.createEdges(GraphWriteIngestionState.MIXED_EDGE_LABEL, edges).size
    }

    @Benchmark
    fun repeatedMixedBatches(state: GraphWriteIngestionState): Int {
        var inserted = 0
        repeat(state.repeatBatches) {
            val batchIndex = state.nextBatchIndex()
            val vertices = state.ops.createVertices(
                GraphWriteIngestionState.REPEATED_VERTEX_LABEL,
                state.vertexRows(batchIndex, state.batchSize),
            )
            inserted += vertices.size
            inserted += state.ops.createEdges(
                GraphWriteIngestionState.REPEATED_EDGE_LABEL,
                state.cycleEdges(vertices, batchIndex),
            ).size
        }

        return inserted
    }
}

@State(Scope.Benchmark)
open class GraphWriteIngestionState {

    companion object {
        const val GRAPH_NAME: String = "graph_write_ingestion_benchmark"
        const val EDGE_SEED_LABEL: String = "EdgeSeed"
        const val WRITE_VERTEX_LABEL: String = "WriteVertex"
        const val WRITE_EDGE_LABEL: String = "WRITE_EDGE"
        const val MIXED_VERTEX_LABEL: String = "MixedVertex"
        const val MIXED_EDGE_LABEL: String = "MIXED_EDGE"
        const val REPEATED_VERTEX_LABEL: String = "RepeatedVertex"
        const val REPEATED_EDGE_LABEL: String = "REPEATED_EDGE"

        private const val EDGE_SEED_MULTIPLIER: Int = 20
    }

    @Param("tinkergraph", "neo4j", "memgraph", "age", "falkordb")
    lateinit var backend: String

    @Param("100", "1000")
    var batchSize: Int = 100

    @Param("5")
    var repeatBatches: Int = 5

    lateinit var ops: GraphOperations

    private lateinit var edgeSeedVertices: List<GraphVertex>
    private val batchSequence = AtomicLong()

    private var dataSource: HikariDataSource? = null
    private var neo4jDriver: Driver? = null
    private var falkorDriver: com.falkordb.Driver? = null
    private var falkorServer: FalkorDBServer? = null

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
            "age" -> {
                val server = PostgreSQLAgeServer.Launcher.postgresqlAge
                val source = HikariDataSource(HikariConfig().apply {
                    jdbcUrl = server.jdbcUrl
                    username = server.username
                    password = server.password
                    driverClassName = "org.postgresql.Driver"
                    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
                    maximumPoolSize = 4
                })
                dataSource = source
                Database.connect(source)
                AgeGraphOperations(GRAPH_NAME)
            }
            "falkordb" -> {
                val server = FalkorDBServer(reuse = true).apply {
                    withEnv("FALKORDB_ARGS", "MAX_QUEUED_QUERIES 25 TIMEOUT 60000 RESULTSET_SIZE 100000")
                    start()
                }
                falkorServer = server
                val driver = DriverImpl(
                    JedisPool(
                        JedisPoolConfig().apply { maxTotal = 4 },
                        server.host,
                        server.port,
                        60_000,
                    ),
                )
                falkorDriver = driver
                FalkorDBGraphOperations(driver, GRAPH_NAME)
            }
            else -> error("Unsupported graph benchmark backend: $backend")
        }
    }

    @Setup(Level.Iteration)
    fun setupGraph() {
        runCatching { ops.dropGraph(GRAPH_NAME) }
        runCatching { ops.createGraph(GRAPH_NAME) }
        batchSequence.set(0L)

        edgeSeedVertices = ops.createVertices(
            EDGE_SEED_LABEL,
            vertexRows(batchIndex = -1L, count = batchSize * EDGE_SEED_MULTIPLIER),
        )
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

    fun nextBatchIndex(): Long =
        batchSequence.getAndIncrement()

    fun vertexRows(batchIndex: Long, count: Int): List<Map<String, Any?>> =
        (0 until count).map { index ->
            val absoluteIndex = batchIndex * count + index
            mapOf(
                "name" to "Write-$batchIndex-$index",
                "batch" to batchIndex,
                "rank" to absoluteIndex,
            )
        }

    fun edgeRows(batchIndex: Long, count: Int): List<BatchEdge> {
        val maxStart = (edgeSeedVertices.size - count).coerceAtLeast(1)
        val start = ((batchIndex * count) % maxStart).toInt()
        val vertices = edgeSeedVertices.subList(start, start + count)

        return cycleEdges(vertices, batchIndex)
    }

    fun cycleEdges(vertices: List<GraphVertex>, batchIndex: Long): List<BatchEdge> =
        vertices.indices.map { index ->
            BatchEdge(
                fromId = vertices[index].id,
                toId = vertices[(index + 1) % vertices.size].id,
                properties = mapOf(
                    "batch" to batchIndex,
                    "rank" to index.toLong(),
                ),
            )
        }
}

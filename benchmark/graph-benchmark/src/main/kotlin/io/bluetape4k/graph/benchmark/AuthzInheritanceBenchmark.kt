package io.bluetape4k.graph.benchmark

import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.benchmark.abuser.PostgreSqlAbuserDetectionSupport
import io.bluetape4k.graph.benchmark.abuser.SqlTraversalMode
import io.bluetape4k.graph.benchmark.authz.AgeAuthzInheritanceEngine
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceEngine
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceFixture
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceFixtureFactory
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceScenario
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceSize
import io.bluetape4k.graph.benchmark.authz.NativeCypherAuthzInheritanceEngine
import io.bluetape4k.graph.benchmark.authz.SqlAuthzInheritanceEngine
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
open class AuthzInheritanceBenchmark {

    @Param("neo4j-cypher", "memgraph-cypher", "age-cypher", "postgres-cte", "postgres-iterative")
    lateinit var backend: String

    @Param("smoke", "small", "medium", "large")
    lateinit var sizeName: String

    @Param("shallow", "deep-inheritance", "deny-heavy", "wide-groups", "long-chain", "deep-wide")
    lateinit var scenarioName: String

    lateinit var fixture: AuthzInheritanceFixture
    lateinit var engine: AuthzInheritanceEngine

    private var dataSource: AutoCloseable? = null
    private var cypherDriver: Driver? = null

    @Setup
    fun setup() {
        fixture = AuthzInheritanceFixtureFactory.create(
            size = AuthzInheritanceSize.fromName(sizeName),
            scenario = AuthzInheritanceScenario.fromName(scenarioName),
        )

        engine = when (backend) {
            "neo4j-cypher" -> {
                val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
                cypherDriver = driver
                NativeCypherAuthzInheritanceEngine(driver, "neo4j-cypher")
            }
            "memgraph-cypher" -> {
                val driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
                cypherDriver = driver
                NativeCypherAuthzInheritanceEngine(driver, "memgraph-cypher")
            }
            "age-cypher" -> {
                val pool = createPostgreSqlPool(loadAge = true)
                dataSource = pool
                AgeAuthzInheritanceEngine(
                    "authz_inheritance_${sizeName}_${scenarioName.replace('-', '_')}",
                    pool,
                )
            }
            "postgres-cte" -> {
                val pool = createPostgreSqlPool(loadAge = false)
                dataSource = pool
                SqlAuthzInheritanceEngine(pool, SqlTraversalMode.RECURSIVE_CTE)
            }
            "postgres-iterative" -> {
                val pool = createPostgreSqlPool(loadAge = false)
                dataSource = pool
                SqlAuthzInheritanceEngine(pool, SqlTraversalMode.ITERATIVE)
            }
            else -> error("Unsupported authorization inheritance backend: $backend")
        }
        engine.reset()
        engine.load(fixture)
    }

    private fun createPostgreSqlPool(loadAge: Boolean): HikariDataSource =
        PostgreSQLAgeServer.Launcher.postgresqlAge.let { server ->
            PostgreSqlAbuserDetectionSupport.createDataSource(
                jdbcUrl = server.jdbcUrl,
                username = requireNotNull(server.username) { "PostgreSQL AGE username is not available" },
                password = requireNotNull(server.password) { "PostgreSQL AGE password is not available" },
                poolName = "authz-inheritance-$backend-$sizeName-$scenarioName",
                loadAge = loadAge,
            )
        }

    @TearDown
    fun teardown() {
        runCatching { engine.close() }
        runCatching { cypherDriver?.close() }
        runCatching { dataSource?.close() }
    }

    @Benchmark
    fun resolveResources(): Int =
        engine.resolve().resourceCount

    @Benchmark
    fun resolveF1BasisPoints(): Int =
        (engine.resolve().metrics.f1 * 10_000).toInt()
}

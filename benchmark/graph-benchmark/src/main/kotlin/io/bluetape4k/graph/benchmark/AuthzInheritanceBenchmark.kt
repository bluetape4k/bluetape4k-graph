package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.benchmark.abuser.PostgreSqlAbuserDetectionSupport
import io.bluetape4k.graph.benchmark.abuser.SqlTraversalMode
import io.bluetape4k.graph.benchmark.authz.AgeAuthzInheritanceEngine
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceEngine
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceFixture
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceFixtureFactory
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceScenario
import io.bluetape4k.graph.benchmark.authz.AuthzInheritanceSize
import io.bluetape4k.graph.benchmark.authz.SqlAuthzInheritanceEngine
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
open class AuthzInheritanceBenchmark {

    @Param("age-cypher", "postgres-cte", "postgres-iterative")
    lateinit var backend: String

    @Param("smoke", "small", "medium")
    lateinit var sizeName: String

    @Param("shallow", "deep-inheritance", "deny-heavy", "wide-groups")
    lateinit var scenarioName: String

    lateinit var fixture: AuthzInheritanceFixture
    lateinit var engine: AuthzInheritanceEngine

    private var dataSource: AutoCloseable? = null

    @Setup
    fun setup() {
        fixture = AuthzInheritanceFixtureFactory.create(
            size = AuthzInheritanceSize.fromName(sizeName),
            scenario = AuthzInheritanceScenario.fromName(scenarioName),
        )
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        val pool = PostgreSqlAbuserDetectionSupport.createDataSource(
            jdbcUrl = server.jdbcUrl,
            username = requireNotNull(server.username) { "PostgreSQL AGE username is not available" },
            password = requireNotNull(server.password) { "PostgreSQL AGE password is not available" },
            poolName = "authz-inheritance-$backend-$sizeName-$scenarioName",
            loadAge = backend == "age-cypher",
        )
        dataSource = pool

        engine = when (backend) {
            "age-cypher" -> AgeAuthzInheritanceEngine(
                "authz_inheritance_${sizeName}_${scenarioName.replace('-', '_')}",
                pool,
            )
            "postgres-cte" -> SqlAuthzInheritanceEngine(pool, SqlTraversalMode.RECURSIVE_CTE)
            "postgres-iterative" -> SqlAuthzInheritanceEngine(pool, SqlTraversalMode.ITERATIVE)
            else -> error("Unsupported authorization inheritance backend: $backend")
        }
        engine.reset()
        engine.load(fixture)
    }

    @TearDown
    fun teardown() {
        runCatching { engine.close() }
        runCatching { dataSource?.close() }
    }

    @Benchmark
    fun resolveResources(): Int =
        engine.resolve().resourceCount

    @Benchmark
    fun resolveF1BasisPoints(): Int =
        (engine.resolve().metrics.f1 * 10_000).toInt()
}

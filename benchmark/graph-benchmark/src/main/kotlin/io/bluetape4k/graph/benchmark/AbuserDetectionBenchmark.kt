package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.benchmark.abuser.AbuserDetectionEngine
import io.bluetape4k.graph.benchmark.abuser.AbuserDetectionFixture
import io.bluetape4k.graph.benchmark.abuser.AbuserDetectionFixtureFactory
import io.bluetape4k.graph.benchmark.abuser.AbuserDetectionScenario
import io.bluetape4k.graph.benchmark.abuser.AbuserDetectionSize
import io.bluetape4k.graph.benchmark.abuser.AgeAbuserDetectionEngine
import io.bluetape4k.graph.benchmark.abuser.ExposedAbuserDetectionEngine
import io.bluetape4k.graph.benchmark.abuser.JpaAbuserDetectionEngine
import io.bluetape4k.graph.benchmark.abuser.PostgreSqlAbuserDetectionSupport
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

/**
 * Compares PostgreSQL abuser-detection implementations over a deterministic fixture.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
open class AbuserDetectionBenchmark {

    @Param("age", "exposed", "jpa")
    lateinit var backend: String

    @Param("smoke", "small", "medium")
    lateinit var sizeName: String

    @Param("shared", "transfer", "noisy-dense", "wide-fanout")
    lateinit var scenarioName: String

    lateinit var fixture: AbuserDetectionFixture
    lateinit var engine: AbuserDetectionEngine

    private var dataSource: AutoCloseable? = null

    @Setup
    fun setup() {
        fixture = AbuserDetectionFixtureFactory.create(
            size = AbuserDetectionSize.fromName(sizeName),
            scenario = AbuserDetectionScenario.fromName(scenarioName),
        )
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        val loadAge = backend == "age"
        val pool = PostgreSqlAbuserDetectionSupport.createDataSource(
            jdbcUrl = server.jdbcUrl,
            username = requireNotNull(server.username) { "PostgreSQL AGE username is not available" },
            password = requireNotNull(server.password) { "PostgreSQL AGE password is not available" },
            poolName = "abuser-detection-$backend-$sizeName-$scenarioName",
            loadAge = loadAge,
        )
        dataSource = pool

        engine = when (backend) {
            "age" -> AgeAbuserDetectionEngine("abuser_detection_${sizeName}_${scenarioName.replace('-', '_')}", pool)
            "exposed" -> ExposedAbuserDetectionEngine(pool)
            "jpa" -> JpaAbuserDetectionEngine(pool)
            else -> error("Unsupported abuser detection backend: $backend")
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
    fun detectCandidates(): Int =
        engine.detect().candidateCount

    @Benchmark
    fun detectF1BasisPoints(): Int =
        (engine.detect().metrics.f1 * 10_000).toInt()
}

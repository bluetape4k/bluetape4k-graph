package io.bluetape4k.graph.benchmark.abuser

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AbuserDetectionEngineSmokeTest {

    @Test
    fun `AGE Exposed detects smoke fixture`() {
        runEngine("age-smoke", loadAge = true) { dataSource ->
            AgeAbuserDetectionEngine("abuser_detection_smoke_test", dataSource)
        }
    }

    @Test
    fun `Exposed JDBC detects smoke fixture`() {
        runEngine("exposed-smoke", loadAge = false) { dataSource ->
            ExposedAbuserDetectionEngine(dataSource)
        }
    }

    @Test
    fun `JPA Hibernate detects smoke fixture`() {
        runEngine("jpa-smoke", loadAge = false) { dataSource ->
            JpaAbuserDetectionEngine(dataSource)
        }
    }

    private fun runEngine(
        poolName: String,
        loadAge: Boolean,
        engineFactory: (AutoCloseableDataSource) -> AbuserDetectionEngine,
    ) {
        val fixture = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.SHARED)
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        val dataSource = AutoCloseableDataSource(
            PostgreSqlAbuserDetectionSupport.createDataSource(
                jdbcUrl = server.jdbcUrl,
                username = requireNotNull(server.username) { "PostgreSQL AGE username is not available" },
                password = requireNotNull(server.password) { "PostgreSQL AGE password is not available" },
                poolName = poolName,
                loadAge = loadAge,
            ),
        )

        dataSource.use {
            engineFactory(it).use { engine ->
                engine.reset()
                engine.load(fixture)

                val result = engine.detect()

                result.predictedAbusiveAccountIds shouldBeEqualTo fixture.expectedAbusiveAccountIds
                result.metrics.falsePositives shouldBeEqualTo 0
                result.metrics.falseNegatives shouldBeEqualTo 0
                result.metrics.f1 shouldBeEqualTo 1.0
            }
        }
    }

    private class AutoCloseableDataSource(
        private val delegate: com.zaxxer.hikari.HikariDataSource,
    ): javax.sql.DataSource by delegate, AutoCloseable {
        override fun close() {
            delegate.close()
        }
    }
}

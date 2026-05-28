package io.bluetape4k.graph.benchmark.authz

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.graph.benchmark.abuser.PostgreSqlAbuserDetectionSupport
import io.bluetape4k.graph.benchmark.abuser.SqlTraversalMode
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthzInheritanceEngineSmokeTest {

    @Test
    fun `long chain fixture reaches resources only through deep traversal`() {
        val fixture = AuthzInheritanceFixtureFactory.create(
            AuthzInheritanceSize.SMOKE,
            AuthzInheritanceScenario.LONG_CHAIN,
        )

        fixture.scenario.hopLimit shouldBeEqualTo 10
        fixture.expectedResourceIds.size shouldBeGreaterThan 0
    }

    @Test
    fun `authorization inheritance engines resolve the same resources`() {
        runCypherEngine("authz-neo4j-smoke") {
            GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
        }
        runCypherEngine("authz-memgraph-smoke") {
            GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        }
        runEngine("authz-age-smoke", loadAge = true) { dataSource ->
            AgeAuthzInheritanceEngine("authz_inheritance_smoke_test", dataSource)
        }
        SqlTraversalMode.entries.forEach { mode ->
            runEngine("authz-postgres-${mode.displayName}", loadAge = false) { dataSource ->
                SqlAuthzInheritanceEngine(dataSource, mode)
            }
        }
    }

    private fun runCypherEngine(
        implementationName: String,
        driverFactory: () -> org.neo4j.driver.Driver,
    ) {
        val fixture = AuthzInheritanceFixtureFactory.create(
            AuthzInheritanceSize.SMOKE,
            AuthzInheritanceScenario.DEEP_INHERITANCE,
        )

        driverFactory().use { driver ->
            NativeCypherAuthzInheritanceEngine(driver, implementationName).use { engine ->
                engine.reset()
                engine.load(fixture)

                val result = engine.resolve()

                result.resourceIds shouldBeEqualTo fixture.expectedResourceIds
                result.metrics.falsePositives shouldBeEqualTo 0
                result.metrics.falseNegatives shouldBeEqualTo 0
                result.metrics.f1 shouldBeEqualTo 1.0
            }
        }
    }

    private fun runEngine(
        poolName: String,
        loadAge: Boolean,
        engineFactory: (AutoCloseableDataSource) -> AuthzInheritanceEngine,
    ) {
        val fixture = AuthzInheritanceFixtureFactory.create(
            AuthzInheritanceSize.SMOKE,
            AuthzInheritanceScenario.DEEP_INHERITANCE,
        )
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

                val result = engine.resolve()

                result.resourceIds shouldBeEqualTo fixture.expectedResourceIds
                result.metrics.falsePositives shouldBeEqualTo 0
                result.metrics.falseNegatives shouldBeEqualTo 0
                result.metrics.f1 shouldBeEqualTo 1.0
            }
        }
    }

    private class AutoCloseableDataSource(
        private val delegate: com.zaxxer.hikari.HikariDataSource,
    ): DataSource by delegate, AutoCloseable {
        override fun close() {
            delegate.close()
        }
    }
}

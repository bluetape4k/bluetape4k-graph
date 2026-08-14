package io.bluetape4k.graph.age

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

/** Apache AGE Testcontainers lane에서 공용 capability 계약을 검증한다. */
class AgeGraphCapabilityConformanceTest : AbstractGraphCapabilityConformanceTest() {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var delegate: AgeGraphOperations

    override val operations: GraphOperations
        get() = delegate

    override val graphName: String = "capability_conformance"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)

    @BeforeAll
    fun startServer() {
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        database = Database.connect(dataSource)
        delegate = AgeGraphOperations(graphName)
    }

    @AfterAll
    fun closeDataSource() {
        dataSource.close()
    }
}

package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class AgeLinkedInGraphSuspendTest: AbstractLinkedInGraphSuspendTest() {
    override val graphName = "linkedin_test_suspend"
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    override lateinit var ops: AgeGraphSuspendOperations


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
        ops = AgeGraphSuspendOperations(graphName)
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

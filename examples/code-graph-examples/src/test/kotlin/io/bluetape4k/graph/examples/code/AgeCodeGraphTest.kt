package io.bluetape4k.graph.examples.code

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class AgeCodeGraphTest: AbstractCodeGraphTest() {
    override val graphName = "code_test"

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    override lateinit var ops: AgeGraphOperations

    @BeforeAll
    fun startServer() {
        // PostgreSQLAgeServer.Launcher.postgresqlAge는 lazy singleton — 자동 시작, 별도 stop 불필요
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
        ops = AgeGraphOperations(graphName)
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

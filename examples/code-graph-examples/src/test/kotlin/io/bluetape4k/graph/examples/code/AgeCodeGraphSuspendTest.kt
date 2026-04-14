package io.bluetape4k.graph.examples.code

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class AgeCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val graphName = "code_test_suspend"

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    override lateinit var ops: AgeGraphSuspendOperations

    @BeforeAll
    fun startServer() {
        // PostgreSQLAgeServer.instance는 lazy singleton — 자동 시작, 별도 stop 불필요
        val server = PostgreSQLAgeServer.instance
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
    fun stopServer() { dataSource.close() }
}

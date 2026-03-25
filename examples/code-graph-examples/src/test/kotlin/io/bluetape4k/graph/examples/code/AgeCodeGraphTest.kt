package io.bluetape4k.graph.examples.code

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeCodeGraphTest : AbstractCodeGraphTest() {
    override val graphName = "code_test"

    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphOperations

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
        ops = AgeGraphOperations(Database.connect(dataSource), graphName)
    }

    @AfterAll
    fun stopServer() { dataSource.close() }
}

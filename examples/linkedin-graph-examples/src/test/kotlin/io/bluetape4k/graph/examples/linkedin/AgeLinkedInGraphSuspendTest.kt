package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    override val graphName = "linkedin_test_suspend"
    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphSuspendOperations

    @BeforeAll
    fun startServer() {
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        ops = AgeGraphSuspendOperations(Database.connect(dataSource), graphName)
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

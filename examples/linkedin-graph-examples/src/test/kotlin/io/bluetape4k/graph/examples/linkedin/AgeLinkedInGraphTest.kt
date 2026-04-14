package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class AgeLinkedInGraphTest: AbstractLinkedInGraphTest() {
    override val graphName = "linkedin_test"
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    override lateinit var ops: AgeGraphOperations

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
        database = Database.connect(dataSource)
        ops = AgeGraphOperations(graphName)
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

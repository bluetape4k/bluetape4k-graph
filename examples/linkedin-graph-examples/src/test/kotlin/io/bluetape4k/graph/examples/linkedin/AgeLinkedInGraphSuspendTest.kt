package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.testcontainers.database.getDataSource
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll

class AgeLinkedInGraphSuspendTest: AbstractLinkedInGraphSuspendTest() {
    private val server = PostgreSQLAgeServer.Launcher.postgresqlAge
    private val dataSource: HikariDataSource = server.getDataSource {
        connectionInitSql = $$"LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
        maximumPoolSize = 5
    }

    // Registers the DataSource globally in Exposed — required side-effect
    val database = Database.connect(dataSource)

    override val graphName = "linkedin_test_suspend"
    override val ops: AgeGraphSuspendOperations = AgeGraphSuspendOperations(database, graphName)

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

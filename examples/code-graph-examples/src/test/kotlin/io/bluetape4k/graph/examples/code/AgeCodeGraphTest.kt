package io.bluetape4k.graph.examples.code

import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.database.getDataSource
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll

class AgeCodeGraphTest: AbstractCodeGraphTest() {

    companion object: KLogging() {
        // PostgreSQLAgeServer.Launcher.postgresqlAge는 lazy singleton — 자동 시작, 별도 stop 불필요
        private val server: PostgreSQLAgeServer by lazy { PostgreSQLAgeServer.Launcher.postgresqlAge }
    }

    private val dataSource: HikariDataSource = server.getDataSource {
        connectionInitSql = $$"LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
        maximumPoolSize = 5
    }
    private val database = Database.connect(dataSource)

    override val graphName = "code_test"
    override val ops: AgeGraphOperations = AgeGraphOperations(database, graphName)

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

package io.bluetape4k.graph.age

import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.closeSafe
import io.bluetape4k.testcontainers.database.getDataSource
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import io.bluetape4k.utils.ShutdownQueue
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractAgeTest {

    companion object: KLogging() {
        private val ageServer by lazy { PostgreSQLAgeServer.Launcher.postgresqlAge }

        @JvmStatic
        protected fun createDataSource(): HikariDataSource = ageServer.getDataSource {
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 10
        }.apply(ShutdownQueue::register)

        @JvmStatic
        protected fun createDatabase(dataSource: DataSource = createDataSource()): Database =
            Database.connect(dataSource)

        const val DEFAULT_TEST_GRAPH_NAME = "test_graph"

        @JvmStatic
        protected fun createAgeGraphOperations(database: Database, graphName: String): AgeGraphOperations =
            AgeGraphOperations(database, graphName)

        @JvmStatic
        protected fun createAgeGraphSuspendOperations(
            database: Database,
            graphName: String,
        ): AgeGraphSuspendOperations =
            AgeGraphSuspendOperations(database, graphName)
    }

    protected val dataSource: HikariDataSource by lazy { createDataSource() }
    protected val database: Database by lazy { createDatabase(dataSource) }

    @AfterAll
    fun afterAll() {
        if (this::dataSource.isOpen) {
            dataSource.closeSafe()
        }
    }
}

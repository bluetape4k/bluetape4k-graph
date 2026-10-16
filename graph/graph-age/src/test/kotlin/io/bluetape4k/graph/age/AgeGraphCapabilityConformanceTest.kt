package io.bluetape4k.graph.age

import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.closeSafe
import io.bluetape4k.testcontainers.database.getDataSource
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll

/** Apache AGE Testcontainers lane에서 공용 capability 계약을 검증한다. */
class AgeGraphCapabilityConformanceTest: AbstractGraphCapabilityConformanceTest() {

    companion object: KLogging()

    private val server = PostgreSQLAgeServer.Launcher.postgresqlAge
    private val dataSource by lazy {
        server.getDataSource {
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        }
    }
    private val database: Database by lazy { Database.connect(dataSource) }

    override val graphName: String = "capability_conformance"

    override val operations: GraphOperations
        field = AgeGraphOperations(database, graphName)

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)

    @AfterAll
    fun afterAll() {
        dataSource.closeSafe()
    }
}

package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

/** Neo4j Testcontainers lane에서 공용 capability 계약을 검증한다. */
class Neo4jGraphCapabilityConformanceTest: AbstractGraphCapabilityConformanceTest() {

    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())

    override val operations: GraphOperations
        field: Neo4jGraphOperations = Neo4jGraphOperations(driver)

    override val graphName: String = "default"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)

    @AfterAll
    fun closeDriver() {
        driver.close()
    }
}

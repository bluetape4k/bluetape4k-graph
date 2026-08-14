package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

/** Neo4j Testcontainers lane에서 공용 capability 계약을 검증한다. */
class Neo4jGraphCapabilityConformanceTest : AbstractGraphCapabilityConformanceTest() {

    private lateinit var driver: Driver
    private lateinit var delegate: Neo4jGraphOperations

    override val operations: GraphOperations
        get() = delegate

    override val graphName: String = "default"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)

    @BeforeAll
    fun startServer() {
        driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
        delegate = Neo4jGraphOperations(driver)
    }

    @AfterAll
    fun closeDriver() {
        driver.close()
    }
}

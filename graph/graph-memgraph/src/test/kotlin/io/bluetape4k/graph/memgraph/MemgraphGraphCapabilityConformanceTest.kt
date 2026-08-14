package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

/** Memgraph Testcontainers lane에서 공용 capability 계약을 검증한다. */
class MemgraphGraphCapabilityConformanceTest : AbstractGraphCapabilityConformanceTest() {

    private lateinit var driver: Driver
    private lateinit var delegate: MemgraphGraphOperations

    override val operations: GraphOperations
        get() = delegate

    override val graphName: String = "default"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)

    @BeforeAll
    fun startServer() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        delegate = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun closeDriver() {
        driver.close()
    }
}

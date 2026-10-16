package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.Driver

/** Memgraph Testcontainers lane에서 공용 capability 계약을 검증한다. */
class MemgraphGraphCapabilityConformanceTest: AbstractGraphCapabilityConformanceTest() {

    private val driver: Driver = MemgraphTestSupport.getMemgraphDriver()

    override val operations: GraphOperations = MemgraphGraphOperations(driver)

    override val graphName: String = "default"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = true)

    @AfterAll
    fun closeDriver() {
        driver.close()
    }
}

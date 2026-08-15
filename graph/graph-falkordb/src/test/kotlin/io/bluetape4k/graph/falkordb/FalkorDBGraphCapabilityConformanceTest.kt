package io.bluetape4k.graph.falkordb

import com.falkordb.FalkorDB
import com.falkordb.Driver
import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.util.UUID

/** FalkorDB Testcontainers lane에서 transaction 미지원 계약까지 검증한다. */
class FalkorDBGraphCapabilityConformanceTest : AbstractGraphCapabilityConformanceTest() {

    private lateinit var driver: Driver
    private lateinit var delegate: FalkorDBGraphOperations

    override val operations: GraphOperations
        get() = delegate

    override val graphName: String =
        "conformance_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = false)

    @BeforeAll
    fun startServer() {
        val server = FalkorDBServer.Launcher.falkordb
        driver = FalkorDB.driver(server.host, server.port)
        delegate = FalkorDBGraphOperations(driver, graphName)
    }

    @AfterAll
    fun closeDriver() {
        runCatching { driver.graph(graphName).use { it.deleteGraph() } }
        runCatching { driver.close() }
    }
}

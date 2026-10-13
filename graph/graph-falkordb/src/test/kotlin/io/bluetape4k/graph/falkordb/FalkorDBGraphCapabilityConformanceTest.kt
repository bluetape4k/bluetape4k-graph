package io.bluetape4k.graph.falkordb

import com.falkordb.Driver
import com.falkordb.FalkorDB
import io.bluetape4k.codec.Base58
import io.bluetape4k.graph.conformance.AbstractGraphCapabilityConformanceTest
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import org.junit.jupiter.api.AfterAll

/** FalkorDB Testcontainers lane에서 transaction 미지원 계약까지 검증한다. */
class FalkorDBGraphCapabilityConformanceTest: AbstractGraphCapabilityConformanceTest() {

    companion object: KLogging() {
        private val falkordb by lazy { FalkorDBServer.Launcher.falkordb }
    }

    override val graphName: String = "conformance_${Base58.randomString(12)}"
    override val expectedCapabilities: Set<GraphCapability> = backendCapabilities(transactional = false)

    private val driver: Driver by lazy { FalkorDB.driver(falkordb.host, falkordb.port) }
    private val delegate: FalkorDBGraphOperations by lazy { FalkorDBGraphOperations(driver, graphName) }

    override val operations: GraphOperations
        get() = delegate


    @AfterAll
    fun closeDriver() {
        runCatching { driver.graph(graphName).use { it.deleteGraph() } }
        runCatching { driver.close() }
    }
}

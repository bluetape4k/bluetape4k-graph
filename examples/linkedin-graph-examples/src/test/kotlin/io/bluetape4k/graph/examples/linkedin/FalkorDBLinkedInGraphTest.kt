package io.bluetape4k.graph.examples.linkedin

import com.falkordb.FalkorDB
import io.bluetape4k.codec.Base58
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.logging.warn
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import org.junit.jupiter.api.AfterAll

class FalkorDBLinkedInGraphTest: AbstractLinkedInGraphTest() {
    private val falkordb = FalkorDBServer.Launcher.falkordb
    private val driver: com.falkordb.Driver = FalkorDB.driver(falkordb.host, falkordb.port)

    override val graphName: String = "linkedin_${Base58.randomString(8)}"
    override val ops: FalkorDBGraphOperations = FalkorDBGraphOperations(driver, graphName)

    @AfterAll
    fun stopServer() {
        runCatching { ops.dropGraph(graphName) }
            .onFailure { log.warn(it) { "Failed to drop graph $graphName" } }
        driver.close()
    }
}

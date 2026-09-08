package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.codec.Base58
import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.logging.warn
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class FalkorDBLinkedInGraphTest : AbstractLinkedInGraphTest() {

    private lateinit var driver: com.falkordb.Driver
    override lateinit var ops: FalkorDBGraphOperations
    override val graphName: String = "linkedin_${Base58.randomString(8)}"

    @BeforeAll
    fun startServer() {
        driver = FalkorDB.driver(FalkorDBServer.Launcher.falkordb.host, FalkorDBServer.Launcher.falkordb.port)
        ops = FalkorDBGraphOperations(driver, graphName)
    }

    @AfterAll
    fun stopServer() {
        runCatching { ops.dropGraph(graphName) }
            .onFailure { log.warn(it) { "Failed to drop graph $graphName" } }
        driver.close()
    }
}

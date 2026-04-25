package io.bluetape4k.graph.examples.linkedin

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.util.UUID

class FalkorDBLinkedInGraphTest : AbstractLinkedInGraphTest() {

    private lateinit var driver: com.falkordb.Driver
    override lateinit var ops: FalkorDBGraphOperations
    override val graphName: String = "linkedin_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    @BeforeAll
    fun startServer() {
        driver = FalkorDB.driver(FalkorDBServer.Launcher.falkordb.host, FalkorDBServer.Launcher.falkordb.port)
        ops = FalkorDBGraphOperations(driver, graphName)
    }

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

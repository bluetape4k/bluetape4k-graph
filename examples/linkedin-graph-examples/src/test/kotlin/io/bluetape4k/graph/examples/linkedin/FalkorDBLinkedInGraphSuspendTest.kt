package io.bluetape4k.graph.examples.linkedin

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.warn
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.util.UUID

class FalkorDBLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {

    private lateinit var driver: com.falkordb.Driver
    override lateinit var ops: FalkorDBGraphSuspendOperations
    override val graphName: String = "linkedin_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    @BeforeAll
    fun startServer() {
        driver = FalkorDB.driver(FalkorDBServer.Launcher.falkordb.host, FalkorDBServer.Launcher.falkordb.port)
        ops = FalkorDBGraphSuspendOperations(driver, graphName)
    }

    @AfterAll
    fun stopServer() {
        try {
            runSuspendIO { ops.dropGraph(graphName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Failed to drop graph $graphName" }
        } finally {
            driver.close()
        }
    }
}

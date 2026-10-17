package io.bluetape4k.graph.examples.code

import com.falkordb.FalkorDB
import io.bluetape4k.codec.Base58
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.warn
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterAll

class FalkorDBCodeGraphSuspendTest: AbstractCodeGraphSuspendTest() {

    private val falkordb by lazy { FalkorDBServer.Launcher.falkordb }
    private val driver: com.falkordb.Driver = FalkorDB.driver(falkordb.host, falkordb.port)

    override val graphName: String = "code_${Base58.randomString(8)}"
    override val ops = FalkorDBGraphSuspendOperations(driver, graphName)

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

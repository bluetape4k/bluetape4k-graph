package io.bluetape4k.graph.falkordb

import com.falkordb.Driver
import com.falkordb.FalkorDB
import io.bluetape4k.codec.Base58
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractFalkorDBTest {

    companion object: KLogging() {
        @JvmStatic
        protected val falkordb: FalkorDBServer by lazy { FalkorDBServer.Launcher.falkordb }

        @JvmStatic
        protected fun createDriver(falkordb: FalkorDBServer = Companion.falkordb): Driver =
            FalkorDB.driver(falkordb.host, falkordb.port)
    }

    protected val driver: com.falkordb.Driver by lazy { createDriver(falkordb) }
    protected val graphName = "test_${Base58.randomString(12)}"

    protected val ops by lazy { FalkorDBGraphOperations(driver, graphName) }
    protected val suspendOps by lazy { FalkorDBGraphSuspendOperations(driver, graphName) }

    @BeforeEach
    open fun beforeEach() {
        ops.dropGraph(graphName)
    }

    @AfterAll
    open fun afterAll() {
        runCatching { driver.graph(graphName).use { it.deleteGraph() } }
        runCatching { driver.close() }
    }
}

package io.bluetape4k.graph.falkordb

import com.falkordb.FalkorDB
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractFalkorDBTest {

    protected lateinit var driver: com.falkordb.Driver
    protected val graphName = "test_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    @BeforeAll
    open fun setupAll() {
        driver = FalkorDB.driver(
            FalkorDBServer.Launcher.falkordb.host,
            FalkorDBServer.Launcher.falkordb.port
        )
    }

    @AfterAll
    open fun teardownAll() {
        runCatching { driver.graph(graphName).use { it.deleteGraph() } }
        runCatching { driver.close() }
    }
}

package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class MemgraphCodeGraphSuspendTest: AbstractCodeGraphSuspendTest() {

    companion object: KLogging()

    private val driver: Driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
    override val ops: MemgraphGraphSuspendOperations = MemgraphGraphSuspendOperations(driver)

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

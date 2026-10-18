package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class MemgraphLinkedInGraphTest: AbstractLinkedInGraphTest() {

    private val memgraph = MemgraphServer.Launcher.memgraph
    private val driver: Driver = GraphDatabase.driver(memgraph.boltUrl, AuthTokens.none())
    override val ops: MemgraphGraphOperations = MemgraphGraphOperations(driver)

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

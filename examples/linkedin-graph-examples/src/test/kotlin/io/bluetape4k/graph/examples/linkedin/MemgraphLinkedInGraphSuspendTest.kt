package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class MemgraphLinkedInGraphSuspendTest: AbstractLinkedInGraphSuspendTest() {

    private lateinit var driver: Driver
    override lateinit var ops: MemgraphGraphSuspendOperations

    @BeforeAll
    fun startServer() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        ops = MemgraphGraphSuspendOperations(driver)
    }

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

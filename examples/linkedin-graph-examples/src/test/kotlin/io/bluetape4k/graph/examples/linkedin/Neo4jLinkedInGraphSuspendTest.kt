package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.servers.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }
}

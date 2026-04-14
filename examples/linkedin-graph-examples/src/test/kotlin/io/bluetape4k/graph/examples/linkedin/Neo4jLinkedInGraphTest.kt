package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.servers.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class Neo4jLinkedInGraphTest: AbstractLinkedInGraphTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }
}

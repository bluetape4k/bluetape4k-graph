package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() { driver.close() }
}

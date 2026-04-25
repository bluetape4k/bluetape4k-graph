package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class Neo4jCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() { driver.close() }
}

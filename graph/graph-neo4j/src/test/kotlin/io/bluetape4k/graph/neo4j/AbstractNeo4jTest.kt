package io.bluetape4k.graph.neo4j

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.closeSafe
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractNeo4jTest {

    companion object: KLogging() {

        val neo4jServer by lazy { Neo4jServer.Launcher.neo4j }

        const val DEFAULT_GRAPH_NAME = "default"
    }

    open val graphName = DEFAULT_GRAPH_NAME

    protected val driver: Driver by lazy { GraphDatabase.driver(neo4jServer.boltUrl, AuthTokens.none()) }
    protected val ops by lazy { Neo4jGraphOperations(driver) }
    protected val suspendOps by lazy { Neo4jGraphSuspendOperations(driver) }
    protected val session by lazy { Neo4jCoroutineSession(driver) }

    @BeforeEach
    open fun beforeEach() {
        ops.dropGraph(graphName)
    }

    @AfterAll
    open fun afterAll() {
        session.closeSafe()
        driver.closeSafe()
    }
}

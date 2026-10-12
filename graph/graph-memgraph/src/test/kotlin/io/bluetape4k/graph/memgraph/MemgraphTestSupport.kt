package io.bluetape4k.graph.memgraph

import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.utils.ShutdownQueue
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

object MemgraphTestSupport {

    fun getMemgraphDriver(): Driver =
        GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
            .apply(ShutdownQueue::register)

}

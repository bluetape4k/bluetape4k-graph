package io.bluetape4k.graph.examples.iam

import com.falkordb.FalkorDB
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.codec.Base58
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.warn
import io.bluetape4k.testcontainers.database.getDataSource
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class TinkerGraphIamAccessGraphTest: AbstractIamAccessGraphTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

class TinkerGraphIamAccessGraphSuspendTest: AbstractIamAccessGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

class Neo4jIamAccessGraphTest: AbstractIamAccessGraphTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }
}

class Neo4jIamAccessGraphSuspendTest: AbstractIamAccessGraphSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }
}

class MemgraphIamAccessGraphTest: AbstractIamAccessGraphTest() {
    private val driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
    override val ops = MemgraphGraphOperations(driver)

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

class MemgraphIamAccessGraphSuspendTest: AbstractIamAccessGraphSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
    override val ops = MemgraphGraphSuspendOperations(driver)

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

class AgeIamAccessGraphTest: AbstractIamAccessGraphTest() {
    private val server = PostgreSQLAgeServer.Launcher.postgresqlAge

    private val dataSource: HikariDataSource = server.getDataSource {
        connectionInitSql = $$"LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
        maximumPoolSize = 5
    }
    private val database = Database.connect(dataSource)

    override val graphName = "iam_access_test"
    override val ops: AgeGraphOperations = AgeGraphOperations(database, graphName)

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

class AgeIamAccessGraphSuspendTest: AbstractIamAccessGraphSuspendTest() {
    private val server = PostgreSQLAgeServer.Launcher.postgresqlAge

    private val dataSource: HikariDataSource = server.getDataSource {
        connectionInitSql = $$"LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
        maximumPoolSize = 5
    }
    private val database = Database.connect(dataSource)


    override val graphName = "iam_access_suspend_test"
    override val ops: AgeGraphSuspendOperations = AgeGraphSuspendOperations(database, graphName)

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

class FalkorDBIamAccessGraphTest: AbstractIamAccessGraphTest() {
    private val falkordb = FalkorDBServer.Launcher.falkordb
    private val driver: com.falkordb.Driver = FalkorDB.driver(falkordb.host, falkordb.port)

    override val graphName: String = "iam_access_${Base58.randomString(8)}"
    override val ops: FalkorDBGraphOperations = FalkorDBGraphOperations(driver, graphName)

    @AfterAll
    fun stopServer() {
        runCatching { ops.dropGraph(graphName) }
            .onFailure { log.warn(it) { "Failed to drop graph $graphName" } }
        driver.close()
    }
}

class FalkorDBIamAccessGraphSuspendTest: AbstractIamAccessGraphSuspendTest() {
    private val falkordb = FalkorDBServer.Launcher.falkordb
    private val driver: com.falkordb.Driver = FalkorDB.driver(falkordb.host, falkordb.port)

    override val graphName: String = "iam_access_${Base58.randomString(8)}"
    override val ops: FalkorDBGraphSuspendOperations = FalkorDBGraphSuspendOperations(driver, graphName)

    @AfterAll
    fun stopServer() {
        try {
            runSuspendIO { ops.dropGraph(graphName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Failed to drop graph $graphName" }
        } finally {
            driver.close()
        }
    }
}

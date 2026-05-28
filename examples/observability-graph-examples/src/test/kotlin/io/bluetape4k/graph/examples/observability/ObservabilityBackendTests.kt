package io.bluetape4k.graph.examples.observability

import com.falkordb.FalkorDB
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.warn
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import java.util.UUID

class TinkerGraphObservabilityIncidentTest : AbstractObservabilityIncidentTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}

class TinkerGraphObservabilityIncidentSuspendTest : AbstractObservabilityIncidentSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}

class Neo4jObservabilityIncidentTest : AbstractObservabilityIncidentTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }
}

class Neo4jObservabilityIncidentSuspendTest : AbstractObservabilityIncidentSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }
}

class MemgraphObservabilityIncidentTest : AbstractObservabilityIncidentTest() {
    private lateinit var driver: Driver
    override lateinit var ops: MemgraphGraphOperations

    @BeforeAll
    fun startServer() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        ops = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun stopServer() {
        driver.close()
    }
}

class MemgraphObservabilityIncidentSuspendTest : AbstractObservabilityIncidentSuspendTest() {
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

class AgeObservabilityIncidentTest : AbstractObservabilityIncidentTest() {
    override val graphName = "observability_incident_test"
    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphOperations

    @BeforeAll
    fun startServer() {
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        Database.connect(dataSource)
        ops = AgeGraphOperations(graphName)
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

class AgeObservabilityIncidentSuspendTest : AbstractObservabilityIncidentSuspendTest() {
    override val graphName = "observability_incident_suspend_test"
    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphSuspendOperations

    @BeforeAll
    fun startServer() {
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        Database.connect(dataSource)
        ops = AgeGraphSuspendOperations(graphName)
    }

    @AfterAll
    fun stopServer() {
        dataSource.close()
    }
}

class FalkorDBObservabilityIncidentTest : AbstractObservabilityIncidentTest() {
    private lateinit var driver: com.falkordb.Driver
    override lateinit var ops: FalkorDBGraphOperations
    override val graphName: String = "observability_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    @BeforeAll
    fun startServer() {
        driver = FalkorDB.driver(FalkorDBServer.Launcher.falkordb.host, FalkorDBServer.Launcher.falkordb.port)
        ops = FalkorDBGraphOperations(driver, graphName)
    }

    @AfterAll
    fun stopServer() {
        runCatching { ops.dropGraph(graphName) }
            .onFailure { log.warn(it) { "Failed to drop graph $graphName" } }
        driver.close()
    }
}

class FalkorDBObservabilityIncidentSuspendTest : AbstractObservabilityIncidentSuspendTest() {
    private lateinit var driver: com.falkordb.Driver
    override lateinit var ops: FalkorDBGraphSuspendOperations
    override val graphName: String = "observability_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    @BeforeAll
    fun startServer() {
        driver = FalkorDB.driver(FalkorDBServer.Launcher.falkordb.host, FalkorDBServer.Launcher.falkordb.port)
        ops = FalkorDBGraphSuspendOperations(driver, graphName)
    }

    @AfterAll
    fun stopServer() {
        runCatching { runSuspendIO { ops.dropGraph(graphName) } }
            .onFailure { log.warn(it) { "Failed to drop graph $graphName" } }
        driver.close()
    }
}

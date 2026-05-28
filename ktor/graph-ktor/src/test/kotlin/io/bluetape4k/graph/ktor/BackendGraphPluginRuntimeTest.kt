package io.bluetape4k.graph.ktor

import com.falkordb.FalkorDB
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import java.util.UUID

class BackendGraphPluginRuntimeTest {

    @Test
    fun `managed Neo4j DSL 은 Ktor route 에서 sync suspend operations 를 연결한다`() = runSuspendIO {
        try {
            backendSmoke(graphName = "default") {
                neo4j {
                    uri = Neo4jServer.Launcher.neo4j.boltUrl
                }
            }
        } finally {
            GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none()).use { driver ->
                runCatching { driver.session().use { session -> session.run("MATCH (n) DETACH DELETE n").consume() } }
            }
        }
    }

    @Test
    fun `managed Memgraph DSL 은 Ktor route 에서 sync suspend operations 를 연결한다`() = runSuspendIO {
        try {
            backendSmoke(graphName = "default") {
                memgraph {
                    uri = MemgraphServer.Launcher.memgraph.boltUrl
                }
            }
        } finally {
            GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none()).use { driver ->
                runCatching { driver.session().use { session -> session.run("MATCH (n) DETACH DELETE n").consume() } }
            }
        }
    }

    @Test
    fun `managed Apache AGE DataSource DSL 은 Ktor route 에서 sync suspend operations 를 연결한다`() = runSuspendIO {
        val graphName = randomGraphName("ktor_age")
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        val username = requireNotNull(server.username) { "PostgreSQL AGE username must be available" }
        val password = requireNotNull(server.password) { "PostgreSQL AGE password must be available" }

        try {
            backendSmoke(graphName = graphName) {
                ageDataSource {
                    jdbcUrl = server.jdbcUrl
                    this.username = username
                    this.password = password
                    this.graphName = graphName
                    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
                    maximumPoolSize = 2
                }
            }
        } finally {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = server.jdbcUrl
                this.username = username
                this.password = password
                driverClassName = "org.postgresql.Driver"
                connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
                maximumPoolSize = 1
            }).use { cleanupDataSource ->
                Database.connect(cleanupDataSource)
                runCatching {
                    val ops = AgeGraphOperations(graphName)
                    if (ops.graphExists(graphName)) {
                        ops.dropGraph(graphName)
                    }
                }
            }
        }
    }

    @Test
    fun `caller-owned Apache AGE helper 는 Ktor route 에서 sync suspend operations 를 연결한다`() = runSuspendIO {
        val graphName = randomGraphName("ktor_age")
        val server = PostgreSQLAgeServer.Launcher.postgresqlAge
        val username = requireNotNull(server.username) { "PostgreSQL AGE username must be available" }
        val password = requireNotNull(server.password) { "PostgreSQL AGE password must be available" }
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 2
        })

        try {
            Database.connect(dataSource)
            backendSmoke(graphName = graphName) {
                age(graphName)
            }
        } finally {
            runCatching {
                val ops = AgeGraphOperations(graphName)
                if (ops.graphExists(graphName)) {
                    ops.dropGraph(graphName)
                }
            }
            dataSource.close()
        }
    }

    @Test
    fun `managed FalkorDB DSL 은 Ktor route 에서 sync suspend operations 를 연결한다`() = runSuspendIO {
        val graphName = randomGraphName("ktor_falkor")
        val server = FalkorDBServer.Launcher.falkordb

        try {
            backendSmoke(graphName = graphName) {
                falkorDB {
                    host = server.host
                    port = server.port
                    this.graphName = graphName
                }
            }
        } finally {
            FalkorDB.driver(server.host, server.port).use { driver ->
                runCatching { driver.graph(graphName).use { graph -> graph.deleteGraph() } }
            }
        }
    }

    private suspend fun backendSmoke(
        graphName: String,
        configureBackend: GraphPluginConfig.() -> Unit,
    ) {
        val label = randomLabel()

        testApplication {
            application {
                install(GraphPlugin) {
                    configureBackend()
                }
                routing {
                    post("/vertices") {
                        val ops = call.graphOperations()
                        if (ops.graphExists(graphName)) {
                            ops.dropGraph(graphName)
                        }
                        ops.createGraph(graphName)
                        ops.createVertex(label, mapOf("name" to "Seoul"))
                        ops.createVertex(label, mapOf("name" to "Busan"))
                        call.respondText("created")
                    }
                    get("/vertices/count") {
                        call.respondText(call.graphSuspendOperations().countVertices(label).toString())
                    }
                }
            }
            startApplication()

            val createResponse = client.post("/vertices")
            createResponse.status shouldBeEqualTo HttpStatusCode.OK
            createResponse.bodyAsText() shouldBeEqualTo "created"

            val countResponse = client.get("/vertices/count")
            countResponse.status shouldBeEqualTo HttpStatusCode.OK
            countResponse.bodyAsText() shouldBeEqualTo "2"
        }
    }

    private fun randomGraphName(prefix: String): String =
        "${prefix}_${randomId()}"

    private fun randomLabel(): String =
        "KtorCity${randomId()}"

    private fun randomId(): String =
        UUID.randomUUID().toString().replace("-", "").take(12)
}

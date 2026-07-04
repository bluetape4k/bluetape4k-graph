package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Query

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jCoroutineSessionTest {

    private val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    private val session = Neo4jCoroutineSession(driver)

    @AfterAll
    fun teardown() {
        session.close()
        driver.close()
    }

    @BeforeEach
    fun clearGraph() = runSuspendIO {
        session.runWriteQuery("MATCH (n) DETACH DELETE n")
    }

    @Test
    fun `write materializes reactive records and closes the session`() = runSuspendIO {
        val names = session.write { s ->
            s.run(Query("CREATE (n:Person {name: 'Alice'}) RETURN n.name AS name"))
                .awaitSingle()
                .records()
                .asFlow()
                .map { it["name"].asString() }
        }

        names shouldBeEqualTo listOf("Alice")
    }

    @Test
    fun `read materializes reactive records with query parameters`() = runSuspendIO {
        session.runWriteQuery(
            "CREATE (:Person {name: \$name})",
            mapOf("name" to "Bob"),
        )

        val names = session.read { s ->
            s.run(
                Query(
                    "MATCH (n:Person {name: \$name}) RETURN n.name AS name",
                    mapOf("name" to "Bob"),
                ),
            )
                .awaitSingle()
                .records()
                .asFlow()
                .map { it["name"].asString() }
        }

        names shouldBeEqualTo listOf("Bob")
    }

    @Test
    fun `runReadQuery and runWriteQuery return materialized records`() = runSuspendIO {
        val created = session.runWriteQuery(
            "CREATE (n:Person {name: \$name}) RETURN n.name AS name",
            mapOf("name" to "Carol"),
        )

        created.shouldHaveSize(1)
        created.single()["name"].asString() shouldBeEqualTo "Carol"

        val found = session.runReadQuery(
            "MATCH (n:Person {name: \$name}) RETURN n.name AS name",
            mapOf("name" to "Carol"),
        )

        found.shouldHaveSize(1)
        found.single()["name"].asString() shouldBeEqualTo "Carol"
    }

    @Test
    fun `blank cypher is rejected before opening a session`() = runSuspendIO {
        assertFailsWith<IllegalArgumentException> {
            session.runReadQuery(" ")
        }

        assertFailsWith<IllegalArgumentException> {
            session.runWriteQuery(" ")
        }
    }
}

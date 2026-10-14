package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Neo4jCoroutineSessionTest: AbstractNeo4jTest() {

    companion object: KLogging()

    @BeforeEach
    fun clearGraph() = runSuspendIO {
        session.runWriteQuery("MATCH (n) DETACH DELETE n")
    }

    @Test
    fun `write materializes reactive records and closes the session`() = runSuspendIO {
        val names = session.write { session ->
            val query = queryOf("CREATE (n:Person {name: 'Alice'}) RETURN n.name AS name")

            session.run(query).awaitSingle()
                .records()
                .asFlow()
                .map { it["name"].asString() }
                .log("WRITE")
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
            val query = queryOf(
                "MATCH (n:Person {name: \$name}) RETURN n.name AS name",
                "name" to "Bob"
            )
            s.run(query)
                .awaitSingle()
                .records()
                .asFlow()
                .map { it["name"].asString() }
                .log("READ")
        }

        names shouldBeEqualTo listOf("Bob")
    }

    @Test
    fun `runReadQuery and runWriteQuery return materialized records`() = runSuspendIO {
        val writeQuery = queryOf(
            $$"CREATE (n:Person {name: $name}) RETURN n.name AS name",
            "name" to "Carol"
        )
        val created = session.runWriteQuery(writeQuery).onEach { log.debug { "Write=$it" } }

        created shouldHaveSize 1
        created.single()["name"].asString() shouldBeEqualTo "Carol"

        val readQuery = queryOf(
            $$"MATCH (n:Person {name: $name}) RETURN n.name AS name",
            "name" to "Carol"
        )
        val found = session.runReadQuery(readQuery).onEach { log.debug { "Read=$it" } }

        found shouldHaveSize 1
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

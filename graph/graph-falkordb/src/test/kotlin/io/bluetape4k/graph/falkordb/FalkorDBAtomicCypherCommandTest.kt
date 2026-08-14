package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import org.junit.jupiter.api.Test

class FalkorDBAtomicCypherCommandTest {
    @Test
    fun `command rejects blank and multi statement payloads`() {
        assertFailsWith<IllegalArgumentException> { FalkorDBAtomicCypherCommand(" ") }
        assertFailsWith<IllegalArgumentException> {
            FalkorDBAtomicCypherCommand("CREATE (n); MATCH (n) RETURN n")
        }
    }

    @Test
    fun `command accepts parameterized result chain`() {
        FalkorDBAtomicCypherCommand(
            cypher = "CREATE (a:Person {name: \$name}) " +
                "CREATE (b:Person) CREATE (a)-[:KNOWS]->(b) " +
                "RETURN id(a) AS aId, id(b) AS bId",
            parameters = mapOf("name" to "Alice"),
        )
    }
}

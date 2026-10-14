package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class Neo4jGraphMergeOperationsTest: AbstractNeo4jTest() {

    companion object: KLogging()

    @Test
    fun `mergeVertex is idempotent and updates existing vertex`() {
        val first = ops.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "alice@example.com"),
            setProperties = mapOf("name" to "Alice", "age" to 30L),
        )
        val second = ops.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "alice@example.com"),
            setProperties = mapOf("name" to "Alice A", "age" to 31L),
        )

        second.id shouldBeEqualTo first.id
        second.properties["email"] shouldBeEqualTo "alice@example.com"
        second.properties["name"] shouldBeEqualTo "Alice A"
        second.properties["age"] shouldBeEqualTo 31L

        ops.findVerticesByLabel("Person") shouldHaveSize 1
    }

    @Test
    fun `mergeEdge is idempotent and updates existing edge`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))

        val first = ops.mergeEdge(alice.id, bob.id, "KNOWS", setProperties = mapOf("since" to 2024L))
        val second = ops.mergeEdge(alice.id, bob.id, "KNOWS", setProperties = mapOf("since" to 2025L))

        second.id shouldBeEqualTo first.id
        second.properties["since"] shouldBeEqualTo 2025L

        ops.findEdgesByLabel("KNOWS") shouldHaveSize 1
    }

    @Test
    fun `suspend merge operations are idempotent`() = runSuspendIO {
        val first = suspendOps.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "alice@example.com"),
            setProperties = mapOf("name" to "Alice"),
        )
        val second = suspendOps.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "alice@example.com"),
            setProperties = mapOf("name" to "Alice A"),
        )

        second.id shouldBeEqualTo first.id
        second.properties["name"] shouldBeEqualTo "Alice A"
        suspendOps.findVerticesByLabel("Person").toList().shouldHaveSize(1)

        val bob = suspendOps.mergeVertex("Person", mapOf("email" to "bob@example.com"))
        val edge1 = suspendOps.mergeEdge(first.id, bob.id, "KNOWS", setProperties = mapOf("since" to 2024L))
        val edge2 = suspendOps.mergeEdge(first.id, bob.id, "KNOWS", setProperties = mapOf("since" to 2025L))

        edge2.id shouldBeEqualTo edge1.id
        edge2.properties["since"] shouldBeEqualTo 2025L
        suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList() shouldHaveSize 1
    }
}

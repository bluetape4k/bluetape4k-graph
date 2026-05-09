package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FalkorDBGraphMergeOperationsTest: AbstractFalkorDBTest() {

    private lateinit var ops: FalkorDBGraphOperations
    private lateinit var suspendOps: FalkorDBGraphSuspendOperations

    @BeforeAll
    override fun setupAll() {
        super.setupAll()
        ops = FalkorDBGraphOperations(driver, graphName)
        suspendOps = FalkorDBGraphSuspendOperations(driver, graphName)
    }

    @BeforeEach
    fun clearGraph() {
        ops.dropGraph(graphName)
    }

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
        ops.findVerticesByLabel("Person").shouldHaveSize(1)
    }

    @Test
    fun `mergeEdge is idempotent and updates existing edge`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))

        val first = ops.mergeEdge(alice.id, bob.id, "KNOWS", setProperties = mapOf("since" to 2024L))
        val second = ops.mergeEdge(alice.id, bob.id, "KNOWS", setProperties = mapOf("since" to 2025L))

        second.id shouldBeEqualTo first.id
        second.properties["since"] shouldBeEqualTo 2025L
        ops.findEdgesByLabel("KNOWS").shouldHaveSize(1)
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
        suspendOps.findEdgesByLabel("KNOWS").toList().shouldHaveSize(1)
    }
}

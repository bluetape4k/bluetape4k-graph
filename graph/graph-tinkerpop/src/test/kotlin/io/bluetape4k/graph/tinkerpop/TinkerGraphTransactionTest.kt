package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.repository.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TinkerGraphTransactionTest {

    private lateinit var ops: TinkerGraphOperations

    @BeforeEach
    fun setUp() {
        ops = TinkerGraphOperations()
    }

    @AfterEach
    fun tearDown() {
        ops.close()
    }

    @Test
    fun `transaction commits created vertices and edge when block succeeds`() {
        val edge = ops.transaction {
            val alice = createVertex("Person", mapOf("name" to "Alice"))
            val bob = createVertex("Person", mapOf("name" to "Bob"))
            createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2026L))
        }

        edge.label shouldBeEqualTo "KNOWS"
        ops.findVerticesByLabel("Person").shouldHaveSize(2)
        ops.findEdgesByLabel("KNOWS").shouldHaveSize(1)
    }

    @Test
    fun `transaction rolls back created vertices and edge when block fails`() {
        val existing = ops.createVertex("Person", mapOf("name" to "Existing"))

        assertFailsWith<IllegalStateException> {
            ops.transaction {
                val alice = createVertex("Person", mapOf("name" to "Alice"))
                val bob = createVertex("Person", mapOf("name" to "Bob"))
                createEdge(alice.id, bob.id, "KNOWS")
                error("boom")
            }
        }

        ops.findVertexById("Person", existing.id)?.properties?.get("name") shouldBeEqualTo "Existing"
        ops.findVerticesByLabel("Person").shouldHaveSize(1)
        ops.findEdgesByLabel("KNOWS").shouldHaveSize(0)
    }

    @Test
    fun `transaction rolls back updates and deletes when block fails`() {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2026L))

        assertFailsWith<IllegalArgumentException> {
            ops.transaction {
                updateVertex("Person", alice.id, mapOf("name" to "Updated"))
                deleteVertex("Person", bob.id)
                deleteEdge("KNOWS", edge.id)
                throw IllegalArgumentException("rollback")
            }
        }

        ops.findVertexById("Person", alice.id)?.properties?.get("name") shouldBeEqualTo "Alice"
        ops.findVertexById("Person", bob.id)?.properties?.get("name") shouldBeEqualTo "Bob"
        ops.findEdgesByLabel("KNOWS").shouldHaveSize(1)
    }

    @Test
    fun `transaction returns block result`() {
        val vertexId = ops.transaction {
            createVertex("Person", mapOf("name" to "Alice")).id
        }

        ops.findVertexById("Person", vertexId)?.properties?.get("name") shouldBeEqualTo "Alice"
    }
}

package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TinkerGraphBatchOperationsTest {

    private val ops = TinkerGraphOperations()
    private val suspendOps = TinkerGraphSuspendOperations(TinkerGraphOperations())

    @AfterAll
    fun teardown() {
        ops.close()
        suspendOps.close()
    }

    @BeforeEach
    fun clearGraph() = runSuspendIO {
        ops.dropGraph("default")
        suspendOps.dropGraph("default")
    }

    @Test
    fun `createVertices returns vertices in input order`() {
        val vertices = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice", "rank" to 1L),
                mapOf("name" to "Bob", "rank" to 2L),
                mapOf("name" to "Carol", "rank" to 3L),
            ),
        )

        vertices.shouldHaveSize(3)
        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob", "Carol")
        vertices.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L, 3L)
        ops.findVerticesByLabel("Person").shouldHaveSize(3)
    }

    @Test
    fun `createEdges returns edges in input order`() {
        val (alice, bob, carol) = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
                mapOf("name" to "Carol"),
            ),
        )

        val edges = ops.createEdges(
            "KNOWS",
            listOf(
                BatchEdge(alice.id, bob.id, mapOf("rank" to 1L)),
                BatchEdge(bob.id, carol.id, mapOf("rank" to 2L)),
                BatchEdge(carol.id, alice.id, mapOf("rank" to 3L)),
            ),
        )

        edges.shouldHaveSize(3)
        edges.map { it.startId } shouldBeEqualTo listOf(alice.id, bob.id, carol.id)
        edges.map { it.endId } shouldBeEqualTo listOf(bob.id, carol.id, alice.id)
        edges.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L, 3L)
        ops.findEdgesByLabel("KNOWS").shouldHaveSize(3)
    }

    @Test
    fun `createEdges rejects missing endpoint without partial writes`() {
        val (alice, bob) = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
            ),
        )

        assertFailsWith<GraphQueryException> {
            ops.createEdges(
                "KNOWS",
                listOf(
                    BatchEdge(alice.id, bob.id),
                    BatchEdge(alice.id, GraphElementId.of("999999999")),
                ),
            )
        }

        ops.findEdgesByLabel("KNOWS").shouldHaveSize(0)
    }

    @Test
    fun `createVertices rejects invalid row before partial writes`() {
        assertFailsWith<IllegalArgumentException> {
            ops.createVertices(
                "Person",
                listOf(
                    mapOf("name" to "Alice"),
                    mapOf("bad key" to "Bob"),
                ),
            )
        }

        ops.findVerticesByLabel("Person").shouldHaveSize(0)
    }

    @Test
    fun `suspend createVertices and createEdges preserve input order`() = runSuspendIO {
        val vertices = suspendOps.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
            ),
        )

        val edges = suspendOps.createEdges(
            "KNOWS",
            listOf(
                BatchEdge(vertices[0].id, vertices[1].id, mapOf("rank" to 1L)),
                BatchEdge(vertices[1].id, vertices[0].id, mapOf("rank" to 2L)),
            ),
        )

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")
        edges.map { it.startId } shouldBeEqualTo listOf(vertices[0].id, vertices[1].id)
        edges.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L)
        suspendOps.findEdgesByLabel("KNOWS").toList().shouldHaveSize(2)
    }
}

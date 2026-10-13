package io.bluetape4k.graph.age

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.asLong
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AgeGraphBatchOperationsTest: AbstractAgeTest() {

    companion object: KLogging() {
        private const val TEST_GRAPH_NAME = "test_batch_graph"
    }

    private val ops: AgeGraphOperations by lazy { createAgeGraphOperations(database, TEST_GRAPH_NAME) }
    private val suspendOps: AgeGraphSuspendOperations by lazy {
        createAgeGraphSuspendOperations(
            database,
            TEST_GRAPH_NAME
        )
    }

    @BeforeEach
    fun resetGraph() {
        if (ops.graphExists(TEST_GRAPH_NAME)) {
            ops.dropGraph(TEST_GRAPH_NAME)
        }
        ops.createGraph(TEST_GRAPH_NAME)
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

        vertices shouldHaveSize 3
        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob", "Carol")
        vertices.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L, 3L)

        ops.countVertices("Person") shouldBeEqualTo 3L
    }

    @Test
    fun `empty and size one batches preserve write semantics`() {
        ops.createVertices("Person", emptyList()).shouldBeEmpty()
        ops.createEdges("KNOWS", emptyList()).shouldBeEmpty()

        val vertices = ops.createVertices("Person", listOf(mapOf("name" to "Solo")))
        val edge = ops.createEdges("SELF", listOf(BatchEdge(vertices.single().id, vertices.single().id))).single()

        vertices.single().properties["name"] shouldBeEqualTo "Solo"
        edge.startId shouldBeEqualTo vertices.single().id
        edge.endId shouldBeEqualTo vertices.single().id
        ops.countVertices("Person") shouldBeEqualTo 1L
        ops.findEdgesByLabel("SELF").shouldHaveSize(1)
    }

    @Test
    fun `createVertices supports mixed property key groups while preserving order`() {
        val vertices = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob", "city" to "Seoul"),
                mapOf("name" to "Carol"),
            ),
        ).onEach { log.debug { "vertex=$it" } }

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Alice", "Bob", "Carol")
        vertices.map { it.id }.toSet() shouldHaveSize 4
        vertices[2].properties["city"] shouldBeEqualTo "Seoul"
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
        ).onEach { log.debug { "edge=$it" } }

        edges shouldHaveSize 3
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
                    BatchEdge(alice.id, 999_999_999L.toGraphElementId()),
                ),
            )
        }

        ops.findEdgesByLabel("KNOWS").shouldBeEmpty()
    }

    @Test
    fun `suspend createVertices and createEdges preserve input order`() = runSuspendIO {
        val vertices = suspendOps.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
            ),
        ).onEach { log.debug { "vertex=$it" } }

        val edges = suspendOps.createEdges(
            "KNOWS",
            listOf(
                BatchEdge(vertices[0].id, vertices[1].id, mapOf("rank" to 1L)),
                BatchEdge(vertices[1].id, vertices[0].id, mapOf("rank" to 2L)),
            ),
        ).onEach { log.debug { "edge=$it" } }

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Bob")

        edges.map { it.startId } shouldBeEqualTo vertices.map { it.id }
        edges.map { it.properties["rank"].asLong() } shouldBeEqualTo listOf(1L, 2L)

        suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList() shouldHaveSize 2
    }
}

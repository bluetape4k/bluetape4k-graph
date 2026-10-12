package io.bluetape4k.graph.memgraph

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.batchEdgeOf
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.Driver

class MemgraphGraphBatchOperationsTest: AbstractMemgraphTest() {

    companion object: KLogging()

    private val driver: Driver = getMemgraphDriver()
    private val ops: MemgraphGraphOperations = MemgraphGraphOperations(driver)
    private val suspendOps: MemgraphGraphSuspendOperations = MemgraphGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun clearGraph() {
        ops.dropGraph("default")
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

        ops.findVerticesByLabel("Person") shouldHaveSize 3
    }

    @Test
    fun `empty and size one batches preserve write semantics`() {
        ops.createVertices("Person", emptyList()).shouldBeEmpty()
        ops.createEdges("KNOWS", emptyList()).shouldBeEmpty()

        val vertex = ops.createVertices("Person", listOf(mapOf("name" to "Solo"))).single()
        val edge = ops.createEdges("SELF", listOf(batchEdgeOf(vertex.id, vertex.id))).single()

        vertex.properties["name"] shouldBeEqualTo "Solo"
        edge.startId shouldBeEqualTo vertex.id
        edge.endId shouldBeEqualTo vertex.id
        ops.findVerticesByLabel("Person") shouldHaveSize 1
        ops.findEdgesByLabel("SELF") shouldHaveSize 1
    }

    @Test
    fun `createVertices supports mixed keys and repeated property rows`() {
        val vertices = ops.createVertices(
            "Person",
            listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob", "city" to "Seoul"),
            ),
        )

        vertices.map { it.properties["name"] } shouldBeEqualTo listOf("Alice", "Alice", "Bob")
        vertices.map { it.id }.toSet() shouldHaveSize 3
        vertices.last().properties["city"] shouldBeEqualTo "Seoul"
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
        edges.forEach { log.debug { "edge=$it" } }

        edges shouldHaveSize 3
        edges.map { it.startId } shouldBeEqualTo listOf(alice.id, bob.id, carol.id)
        edges.map { it.endId } shouldBeEqualTo listOf(bob.id, carol.id, alice.id)
        edges.map { it.properties["rank"] } shouldBeEqualTo listOf(1L, 2L, 3L)
        ops.findEdgesByLabel("KNOWS") shouldHaveSize 3
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

        suspendOps.findEdgesByLabel("KNOWS").toList() shouldHaveSize 2
    }
}

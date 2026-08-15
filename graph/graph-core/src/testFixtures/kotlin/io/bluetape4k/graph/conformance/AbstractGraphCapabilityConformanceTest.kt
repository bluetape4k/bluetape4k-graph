package io.bluetape4k.graph.conformance

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.capabilities
import io.bluetape4k.graph.repository.mergeVertex
import io.bluetape4k.graph.repository.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * 모든 graph backend가 공유하는 CORE-2 capability 계약 검증 fixture다.
 *
 * backend 테스트는 [operations]와 [expectedCapabilities]만 제공하면 같은
 * capability 조회, batch/chunk, merge, transaction 계약을 실행한다. container
 * backend의 lifecycle은 각 adapter가 소유하고, 테스트 실행 순서는 Gradle의
 * backend task 단위로 유지한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractGraphCapabilityConformanceTest {

    protected abstract val operations: GraphOperations
    protected abstract val graphName: String
    protected abstract val expectedCapabilities: Set<GraphCapability>

    @BeforeEach
    fun resetGraph() {
        if (operations.graphExists(graphName)) {
            operations.dropGraph(graphName)
        }
        operations.createGraph(graphName)
    }

    @AfterAll
    fun closeOperations() {
        operations.close()
    }

    @Test
    fun `backend capability mapping matches the shared contract`() {
        val capabilities = operations.capabilities()

        capabilities.supported shouldBeEqualTo expectedCapabilities
        GraphCapability.entries.forEach { capability ->
            if (capability in expectedCapabilities) {
                capabilities.version(capability) shouldBeEqualTo "core-0.7"
                capabilities.constraints(capability).shouldNotBeEmpty()
            } else {
                capabilities.version(capability).shouldBeNull()
                capabilities.constraints(capability).size shouldBeEqualTo 0
            }
        }
    }

    @Test
    fun `batch insert and chunked export preserve the record contract`() {
        expectedCapabilities.contains(GraphCapability.BATCH_INSERT).shouldBeTrue()
        expectedCapabilities.contains(GraphCapability.CHUNKED_EXPORT).shouldBeTrue()

        val vertices = operations.createVertices(
            label = "Person",
            propertiesList = listOf(
                mapOf("name" to "Alice"),
                mapOf("name" to "Bob"),
                mapOf("name" to "Carol"),
            ),
        )
        vertices.size shouldBeEqualTo 3

        val chunks = operations.findVerticesByLabelChunked("Person", chunkSize = 2).toList()
        chunks.map { it.size } shouldBeEqualTo listOf(2, 1)
        chunks.flatten().map { it.properties["name"] }.toSet() shouldBeEqualTo
            setOf("Alice", "Bob", "Carol")
    }

    @Test
    fun `merge capability is explicit and idempotent`() {
        if (GraphCapability.MERGE !in expectedCapabilities) {
            assertFailsWith<UnsupportedOperationException> {
                operations.mergeVertex("Person", mapOf("email" to "unsupported@example.com"))
            }
            return
        }

        val first = operations.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "alice@example.com"),
            setProperties = mapOf("name" to "Alice"),
        )
        val second = operations.mergeVertex(
            label = "Person",
            matchProperties = mapOf("email" to "alice@example.com"),
            setProperties = mapOf("name" to "Alicia"),
        )

        second.id shouldBeEqualTo first.id
        second.properties["name"] shouldBeEqualTo "Alicia"
        operations.countVertices("Person") shouldBeEqualTo 1L
    }

    @Test
    fun `transaction capability commits and rolls back explicitly`() {
        if (GraphCapability.TRANSACTION !in expectedCapabilities) {
            assertFailsWith<UnsupportedOperationException> {
                operations.transaction { createVertex("Person", mapOf("name" to "unsupported")) }
            }
            return
        }

        operations.transaction {
            createVertex("Person", mapOf("name" to "committed"))
        }
        assertFailsWith<IllegalStateException> {
            operations.transaction {
                createVertex("Person", mapOf("name" to "rolled-back"))
                throw IllegalStateException("conformance rollback")
            }
        }

        operations.findVerticesByLabel("Person").map { it.properties["name"] } shouldBeEqualTo
            listOf("committed")
    }

    /** backend별로 공유하는 capability 집합을 생성한다. */
    protected fun backendCapabilities(transactional: Boolean): Set<GraphCapability> = buildSet {
        add(GraphCapability.MERGE)
        add(GraphCapability.SCHEMA)
        if (transactional) add(GraphCapability.TRANSACTION)
        add(GraphCapability.BATCH_INSERT)
        add(GraphCapability.CHUNKED_READ)
        add(GraphCapability.CHUNKED_EXPORT)
        add(GraphCapability.WEIGHTED_PATH)
        add(GraphCapability.GRAPH_ALGORITHM)
    }
}

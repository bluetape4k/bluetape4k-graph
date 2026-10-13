package io.bluetape4k.graph.repository

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.graph.vt.asVirtualThreadVertexRepository
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionException

class GraphEndpointValidationTest: AbstractGraphTest() {

    companion object: KLogging()

    private lateinit var ops: TinkerGraphOperations
    private lateinit var suspendOps: TinkerGraphSuspendOperations
    private lateinit var virtualThreadOps: GraphVirtualThreadVertexRepository

    @BeforeEach
    fun setUp() {
        ops = TinkerGraphOperations()
        suspendOps = TinkerGraphSuspendOperations()
        virtualThreadOps = ops.asVirtualThreadVertexRepository()
    }

    @AfterEach
    fun tearDown() {
        suspendOps.close()
        ops.close()
    }

    @Test
    fun `동기 endpoint는 존재하고 label이 일치하면 정점을 반환한다`() {
        val person = ops.createVertex("Person", mapOf("name" to "Alice"))

        log.debug { "person=$person" }
        ops.requireEndpoint(person.id, "Person", "personId") shouldBeEqualTo person
    }

    @Test
    fun `동기 endpoint가 없으면 IllegalArgumentException을 던진다`() {
        assertFailsWith<IllegalArgumentException> {
            ops.requireEndpoint(999_999_999.toGraphElementId(), "Person", "personId")
        }
    }

    @Test
    fun `동기 endpoint label이 다르면 IllegalArgumentException을 던진다`() {
        val company = ops.createVertex("Company")

        log.debug { "company=$company" }
        assertFailsWith<IllegalArgumentException> {
            ops.requireEndpoint(company.id, "Person", "personId")
        }
    }

    @Test
    fun `suspend endpoint는 존재하고 label이 일치하면 정점을 반환한다`() = runSuspendIO {
        val person = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        log.debug { "person=$person" }

        suspendOps.requireEndpoint(person.id, "Person", "personId") shouldBeEqualTo person
    }

    @Test
    fun `suspend endpoint가 없으면 IllegalArgumentException을 던진다`() = runSuspendIO {
        assertFailsWith<IllegalArgumentException> {
            suspendOps.requireEndpoint(999_999_999.toGraphElementId(), "Person", "personId")
        }
    }

    @Test
    fun `suspend endpoint label이 다르면 IllegalArgumentException을 던진다`() = runSuspendIO {
        val company = suspendOps.createVertex("Company")
        log.debug { "company=$company" }

        assertFailsWith<IllegalArgumentException> {
            suspendOps.requireEndpoint(company.id, "Person", "personId")
        }
    }

    @Test
    fun `virtual thread endpoint는 존재하고 label이 일치하면 정점을 반환한다`() {
        val person = ops.createVertex("Person", mapOf("name" to "Alice"))
        log.debug { "person=$person" }

        virtualThreadOps.requireEndpointAsync(person.id, "Person", "personId")
            .get() shouldBeEqualTo person
    }

    @Test
    fun `virtual thread endpoint가 없으면 IllegalArgumentException으로 실패한다`() {
        val error = assertFailsWith<CompletionException> {
            virtualThreadOps.requireEndpointAsync(999_999_999.toGraphElementId(), "Person", "personId").join()
        }

        error.cause shouldBeInstanceOf IllegalArgumentException::class
    }

    @Test
    fun `virtual thread endpoint label이 다르면 IllegalArgumentException으로 실패한다`() {
        val company = ops.createVertex("Company")
        log.debug { "company=$company" }

        val error = assertFailsWith<CompletionException> {
            virtualThreadOps.requireEndpointAsync(company.id, "Person", "personId").join()
        }

        error.cause shouldBeInstanceOf IllegalArgumentException::class
    }
}

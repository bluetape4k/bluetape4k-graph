package io.bluetape4k.graph.memgraph

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.codec.Base58
import io.bluetape4k.graph.model.GraphConstraintType
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.Driver
import org.neo4j.driver.SessionConfig

class MemgraphGraphSchemaManagerTest: AbstractMemgraphTest() {

    private val driver: Driver = getMemgraphDriver()
    private val ops: MemgraphGraphOperations = MemgraphGraphOperations(driver)
    private val cancellationDriver = mockk<Driver>()

    @BeforeEach
    fun clearMockDriver() {
        clearMocks(cancellationDriver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @Test
    fun `creates lists and drops property index`() {
        val label = uniqueLabel()
        val manager = ops.schemaManager()

        manager.createIndex(label, "email")

        manager.listIndexes().any { it.label == label && it.property == "email" }.shouldBeTrue()

        manager.dropIndex(label, "email")
        manager.listIndexes().none { it.label == label && it.property == "email" }.shouldBeTrue()
    }

    @Test
    fun `creates and lists unique constraint`() {
        val label = uniqueLabel()
        val manager = ops.schemaManager()

        manager.createUniqueConstraint(label, "email")

        manager.listConstraints()
            .any {
                it.label == label && it.property == "email" && it.type == GraphConstraintType.UNIQUE
            }.shouldBeTrue()
    }

    @Test
    fun `rejects unsafe identifiers before building DDL`() {
        assertFailsWith<IllegalArgumentException> {
            ops.schemaManager().createIndex("Person) MATCH (n", "email")
        }
    }

    @Test
    fun `createIndex propagates cancellation before already exists fallback`() {
        every {
            cancellationDriver.session(any<SessionConfig>())
        } throws CancellationException("already exists")

        val manager = MemgraphGraphSchemaManager(cancellationDriver)

        assertFailsWith<CancellationException> {
            manager.createIndex("Person", "email")
        }
    }

    @Test
    fun `dropIndex propagates cancellation before missing fallback`() {
        every {
            cancellationDriver.session(any<SessionConfig>())
        } throws CancellationException("does not exist")

        val manager = MemgraphGraphSchemaManager(cancellationDriver)

        assertFailsWith<CancellationException> {
            manager.dropIndex("Person", "email")
        }
    }

    private fun uniqueLabel(): String =
        "SchemaPerson_${Base58.randomString(12)}"
}

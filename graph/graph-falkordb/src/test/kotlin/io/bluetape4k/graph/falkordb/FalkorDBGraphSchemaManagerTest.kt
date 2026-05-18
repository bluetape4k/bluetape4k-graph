package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.schema.schemaManager
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class FalkorDBGraphSchemaManagerTest: AbstractFalkorDBTest() {

    private lateinit var ops: FalkorDBGraphOperations
    private val cancellationDriver = mockk<com.falkordb.Driver>()

    @BeforeEach
    fun setUp() {
        clearMocks(cancellationDriver)
        ops = FalkorDBGraphOperations(driver, graphName)
    }

    @AfterEach
    fun tearDown() {
        runCatching { driver.graph(graphName).use { it.deleteGraph() } }
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
    fun `unique constraints fail explicitly until raw GRAPH CONSTRAINT support is added`() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            ops.schemaManager().createUniqueConstraint("Person", "email")
        }

        ex.message shouldContain "GRAPH.CONSTRAINT CREATE"
    }

    @Test
    fun `createIndex propagates cancellation before already exists fallback`() {
        every { cancellationDriver.graph(graphName) } throws CancellationException("already exists")

        val manager = FalkorDBGraphSchemaManager(cancellationDriver, graphName)

        assertFailsWith<CancellationException> {
            manager.createIndex("Person", "email")
        }
    }

    @Test
    fun `dropIndex propagates cancellation before missing fallback`() {
        every { cancellationDriver.graph(graphName) } throws CancellationException("does not exist")

        val manager = FalkorDBGraphSchemaManager(cancellationDriver, graphName)

        assertFailsWith<CancellationException> {
            manager.dropIndex("Person", "email")
        }
    }

    private fun uniqueLabel(): String =
        "SchemaPerson${UUID.randomUUID().toString().replace("-", "").take(10)}"
}

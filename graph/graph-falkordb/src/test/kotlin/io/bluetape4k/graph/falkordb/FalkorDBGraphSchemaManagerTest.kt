package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.schema.schemaManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class FalkorDBGraphSchemaManagerTest: AbstractFalkorDBTest() {

    private lateinit var ops: FalkorDBGraphOperations

    @BeforeEach
    fun setUp() {
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

    private fun uniqueLabel(): String =
        "SchemaPerson${UUID.randomUUID().toString().replace("-", "").take(10)}"
}

package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.graph.schema.schemaManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TinkerGraphSchemaManagerTest {

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
    fun `records indexes in memory for current operations instance`() {
        val manager = ops.schemaManager()

        manager.createIndex("Person", "email")

        val indexes = manager.listIndexes()
        indexes.shouldHaveSize(1)
        indexes.single().label shouldBeEqualTo "Person"
        indexes.single().property shouldBeEqualTo "email"
    }

    @Test
    fun `drops recorded index`() {
        val manager = ops.schemaManager()

        manager.createIndex("Person", "email")
        manager.dropIndex("Person", "email")

        manager.listIndexes().shouldBeEmpty()
    }

    @Test
    fun `unique constraints fail explicitly because TinkerGraph cannot enforce them`() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            ops.schemaManager().createUniqueConstraint("Person", "email")
        }

        ex.message shouldContain "TinkerGraph"
    }

    @Test
    fun `suspend schema manager delegates to same in-memory manager`() = runSuspendIO {
        val suspendOps = TinkerGraphSuspendOperations(ops)

        suspendOps.schemaManager().createIndex("Person", "email")

        ops.schemaManager().listIndexes().single().property shouldBeEqualTo "email"
    }
}

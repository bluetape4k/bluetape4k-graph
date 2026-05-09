package io.bluetape4k.graph.age

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.schema.schemaManager
import org.junit.jupiter.api.Test

class AgeGraphSchemaManagerTest {

    @Test
    fun `AGE schema manager fails explicitly for unverified PostgreSQL-side indexes`() {
        val manager = AgeGraphOperations("schema_test").schemaManager()

        val ex = assertFailsWith<UnsupportedOperationException> {
            manager.createIndex("Person", "email")
        }

        ex.message shouldContain "Apache AGE"
        manager.listIndexes().shouldBeEmpty()
    }
}

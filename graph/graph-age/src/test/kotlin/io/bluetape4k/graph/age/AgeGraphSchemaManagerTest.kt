package io.bluetape4k.graph.age

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.GraphUnsupportedOperationException
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class AgeGraphSchemaManagerTest: AbstractAgeTest() {

    companion object: KLogging()


    @Test
    fun `AGE schema manager fails explicitly for unverified PostgreSQL-side indexes`() {
        val manager = createAgeGraphOperations(database, "schema_test").schemaManager()

        val ex = assertFailsWith<GraphUnsupportedOperationException> {
            manager.createIndex("Person", "email")
        }

        ex.message shouldContain "Apache AGE"
        manager.listIndexes().shouldBeEmpty()
    }
}

package io.bluetape4k.graph.age

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import java.sql.SQLException

class AgeCreateGraphFailuresTest {

    @Test
    fun `duplicate graph SQLState is treated as compatibility duplicate`() {
        SQLException("ERROR: graph already exists", "42710")
            .isDuplicateGraphFailure()
            .shouldBeTrue()
    }

    @Test
    fun `duplicate graph message is treated as compatibility duplicate`() {
        IllegalStateException("ERROR: graph 'test_graph' already exists")
            .isDuplicateGraphFailure()
            .shouldBeTrue()
    }

    @Test
    fun `permission failures are not treated as duplicates`() {
        SQLException("ERROR: permission denied for schema ag_catalog", "42501")
            .isDuplicateGraphFailure()
            .shouldBeFalse()
    }

    @Test
    fun `non duplicate createGraph failure keeps context`() {
        val cause = SQLException("ERROR: permission denied for schema ag_catalog", "42501")
        val ex = cause.asCreateGraphFailure("test_graph")

        ex.message shouldContain "AGE createGraph failed"
        ex.cause shouldBeInstanceOf SQLException::class
    }
}

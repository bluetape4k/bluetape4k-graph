package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.CycleOptions
import org.junit.jupiter.api.Test

class Neo4jCycleFallbackSupportTest {

    @Test
    fun `known unsupported cycle query can use JVM fallback`() {
        RuntimeException("cycle pattern is not supported by this backend")
            .supportsJvmCycleFallback()
            .shouldBeTrue()
    }

    @Test
    fun `unexpected cycle query failure is not treated as fallback`() {
        IllegalStateException("connection reset while reading records")
            .supportsJvmCycleFallback()
            .shouldBeFalse()
    }

    @Test
    fun `unexpected cycle query failure keeps backend context`() {
        val cause = IllegalStateException("connection reset while reading records")
        val ex = cause.asCycleDetectionFailure("Neo4j", CycleOptions(edgeLabel = "E"))

        ex.message shouldContain "Neo4j cycle detection query failed"
        ex.cause shouldBeInstanceOf IllegalStateException::class
    }
}

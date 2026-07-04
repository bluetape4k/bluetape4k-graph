package io.bluetape4k.graph.falkordb

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.CycleOptions
import org.junit.jupiter.api.Test

class FalkorDBCycleFallbackSupportTest {

    @Test
    fun `known unsupported cycle query can use JVM fallback`() {
        RuntimeException("unknown function in cycle query")
            .supportsJvmCycleFallback()
            .shouldBeTrue()
    }

    @Test
    fun `unexpected cycle query failure is not treated as fallback`() {
        IllegalStateException("redis connection closed")
            .supportsJvmCycleFallback()
            .shouldBeFalse()
    }

    @Test
    fun `unexpected cycle query failure keeps backend context`() {
        val cause = IllegalStateException("redis connection closed")
        val ex = cause.asCycleDetectionFailure("FalkorDB", CycleOptions(edgeLabel = "E"))

        ex.message shouldContain "FalkorDB cycle detection query failed"
        ex.cause shouldBeInstanceOf IllegalStateException::class
    }
}

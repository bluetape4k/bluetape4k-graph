package io.bluetape4k.graph.memgraph

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.CycleOptions
import org.junit.jupiter.api.Test

class MemgraphCycleFallbackSupportTest {

    @Test
    fun `known unsupported cycle query can use JVM fallback`() {
        RuntimeException("cycle pattern is not implemented by this backend")
            .supportsJvmCycleFallback()
            .shouldBeTrue()
    }

    @Test
    fun `unexpected cycle query failure is not treated as fallback`() {
        IllegalStateException("bolt connection closed")
            .supportsJvmCycleFallback()
            .shouldBeFalse()
    }

    @Test
    fun `unexpected cycle query failure keeps backend context`() {
        val cause = IllegalStateException("bolt connection closed")
        val ex = cause.asCycleDetectionFailure("Memgraph", CycleOptions(edgeLabel = "E"))

        ex.message shouldContain "Memgraph cycle detection query failed"
        ex.cause shouldBeInstanceOf IllegalStateException::class
    }
}

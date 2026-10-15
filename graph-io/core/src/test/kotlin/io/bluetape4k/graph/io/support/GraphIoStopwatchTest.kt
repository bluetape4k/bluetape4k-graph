package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphIoStopwatchTest {

    companion object: KLogging()

    @Test
    fun `elapsed returns non-negative duration`() {
        val sw = GraphIoStopwatch()
        val elapsed = sw.elapsed().shouldNotBeNull()

        log.debug { "elapsed=${elapsed.toNanos()} nanos" }
        elapsed.toNanos() shouldBeGreaterOrEqualTo 0L
    }

    @Test
    fun `elapsed increases over time`() {
        val sw = GraphIoStopwatch()

        val first = sw.elapsed()
        log.debug { "first elapsed=${first.toNanos()} nanos" }

        Thread.sleep(1)

        val second = sw.elapsed()
        log.debug { "second elapsed=${second.toNanos()} nanos" }

        second.toNanos() shouldBeGreaterThan first.toNanos()
    }
}

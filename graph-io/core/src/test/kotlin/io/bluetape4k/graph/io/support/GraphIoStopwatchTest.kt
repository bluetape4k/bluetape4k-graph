package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test

class GraphIoStopwatchTest {

    @Test
    fun `elapsed returns non-negative duration`() {
        val sw = GraphIoStopwatch()
        val elapsed = sw.elapsed()
        elapsed.shouldNotBeNull()
        elapsed.toNanos() shouldBeGreaterOrEqualTo 0L
    }

    @Test
    fun `elapsed increases over time`() {
        val sw = GraphIoStopwatch()
        val first = sw.elapsed()
        Thread.sleep(1)
        val second = sw.elapsed()
        second.toNanos() shouldBeGreaterOrEqualTo first.toNanos()
    }
}

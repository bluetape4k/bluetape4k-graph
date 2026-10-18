package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class TinkerGraphBatchInsertSmokeTest {

    companion object: KLogging()

    @Test
    fun `10k loop and batch insert smoke`() {
        val result = runBatchInsertSmoke(
            backend = "tinkergraph",
            createOperations = { TinkerGraphOperations() },
        )

        result.rows shouldBeEqualTo 10_000
        println(result.toSummaryLine())
    }
}

package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class WeightedShortestPathBenchTest {

    @Test
    fun `weighted shortest path benchmarks return same canonical cost`() {
        val bench = WeightedShortestPathBench()
        bench.vertexCount = 100
        bench.setup()
        try {
            bench.dijkstra() shouldBeEqualTo 99.0
            bench.aStar() shouldBeEqualTo 99.0
        } finally {
            bench.teardown()
        }
    }
}

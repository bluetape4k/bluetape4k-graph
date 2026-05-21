package io.bluetape4k.graph.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GraphWriteIngestionSmokeTest {

    @Test
    fun `TinkerGraph write ingestion benchmark methods insert expected row counts`() {
        val state = GraphWriteIngestionState().apply {
            backend = "tinkergraph"
            batchSize = 10
            repeatBatches = 2
            setupBackend()
            setupGraph()
        }
        val benchmark = GraphWriteIngestionBenchmark()

        try {
            benchmark.vertexOnlyBatchInsert(state) shouldBeEqualTo 10
            benchmark.edgeOnlyBatchInsert(state) shouldBeEqualTo 10
            benchmark.mixedVertexEdgeInsert(state) shouldBeEqualTo 20
            benchmark.repeatedMixedBatches(state) shouldBeEqualTo 40
        } finally {
            state.teardownBackend()
        }
    }
}

package io.bluetape4k.graph.benchmark.io

import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.exists

class GraphIoBenchmarkSmokeTest {

    @Test
    fun `smoke size exercises representative graph io benchmark methods`() {
        val state = BulkGraphIoBenchmarkState().apply {
            sizeName = "smoke"
            setup()
        }

        try {
            BulkGraphIoBenchmark().csvSyncRoundTrip(state)
            OkioGraphIoBenchmark().jackson3OkioRoundTrip(state)
            OkioGraphIoBenchmark().graphMlOkioRoundTrip(state)

            state.tempDir.resolve("v.csv").exists().shouldBeTrue()
            state.tempDir.resolve("g3ok.ndjson").exists().shouldBeTrue()
            state.tempDir.resolve("gok.graphml").exists().shouldBeTrue()
        } finally {
            state.teardown()
        }
    }
}

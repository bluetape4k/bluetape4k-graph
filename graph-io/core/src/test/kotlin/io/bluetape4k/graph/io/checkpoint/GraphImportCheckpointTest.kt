package io.bluetape4k.graph.io.checkpoint

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.report.GraphIoFormat
import org.junit.jupiter.api.Test

class GraphImportCheckpointTest {
    private val checkpoint = GraphImportCheckpoint(
        format = GraphIoFormat.NDJSON_JACKSON3,
        sourceIdentity = "sha256:source",
        phase = GraphImportCheckpointPhase.VERTICES,
        verticesProcessed = 10,
        edgesProcessed = 0,
    )

    @Test
    fun `in memory store round trips and deletes`() {
        val store = InMemoryGraphImportCheckpointStore()
        store.save("job-1", checkpoint)
        store.load("job-1") shouldBeEqualTo checkpoint
        store.delete("job-1")
        store.load("job-1") shouldBeEqualTo null
    }

    @Test
    fun `validator rejects changed source`() {
        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointValidator.requireCompatible(
                checkpoint,
                GraphIoFormat.NDJSON_JACKSON3,
                "sha256:changed",
            )
        }
    }
}

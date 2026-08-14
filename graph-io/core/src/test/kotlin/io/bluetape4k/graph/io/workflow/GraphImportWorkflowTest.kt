package io.bluetape4k.graph.io.workflow

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.report.GraphIoFormat
import org.junit.jupiter.api.Test

class GraphImportWorkflowTest {
    private val manifest = GraphImportManifest(
        jobId = "job-1",
        sources = listOf(
            GraphImportSourceSpec("vertices", GraphImportSourceRole.VERTICES, GraphIoFormat.CSV, "sha256:v"),
            GraphImportSourceSpec(
                "edges",
                GraphImportSourceRole.EDGES,
                GraphIoFormat.NDJSON_JACKSON3,
                "sha256:e",
                dependsOn = setOf("vertices"),
            ),
        ),
    )

    @Test
    fun `workflow enforces vertex before edge ordering and persists state`() {
        val store = InMemoryGraphImportJobStateStore()
        val workflow = GraphImportWorkflow(manifest, store)
        workflow.validate().state shouldBeEqualTo GraphImportWorkflowState.VALIDATED
        workflow.transition(GraphImportWorkflowState.VERTICES_LOADED)
        workflow.transition(GraphImportWorkflowState.EDGES_LOADED).state shouldBeEqualTo
            GraphImportWorkflowState.EDGES_LOADED
        store.load("job-1")?.state shouldBeEqualTo GraphImportWorkflowState.EDGES_LOADED
    }

    @Test
    fun `workflow rejects edges before validation`() {
        val workflow = GraphImportWorkflow(manifest, InMemoryGraphImportJobStateStore())
        assertFailsWith<IllegalArgumentException> {
            workflow.transition(GraphImportWorkflowState.EDGES_LOADED)
        }
    }
}

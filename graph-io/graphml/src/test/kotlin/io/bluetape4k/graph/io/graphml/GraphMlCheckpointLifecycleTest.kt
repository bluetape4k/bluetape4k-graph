package io.bluetape4k.graph.io.graphml

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointPhase
import io.bluetape4k.graph.io.checkpoint.InMemoryGraphImportCheckpointStore
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.copyWithCheckpointSourceIdentity
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GraphMlCheckpointLifecycleTest {

    @Test
    fun `sync resume skips committed edges without duplicating graph state`(@TempDir dir: Path) {
        val input = dir.resolve("graph.graphml")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphOperations()

        GraphMlBulkImporter().importGraph(GraphImportSource.PathSource(input), graph, options).status
            .shouldBeEqualTo(GraphIoStatus.FAILED)
        store.load(KEY)?.phase.shouldBeEqualTo(GraphImportCheckpointPhase.FAILED)
        store.load(KEY)?.edgesProcessed.shouldBeEqualTo(1L)

        Files.writeString(input, fixture("n2"))
        val resumed = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(input),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        resumed.status.shouldBeEqualTo(GraphIoStatus.COMPLETED)
        resumed.verticesCreated.shouldBeEqualTo(0L)
        resumed.edgesCreated.shouldBeEqualTo(1L)
        store.load(KEY).shouldBeEqualTo(null)
    }

    @Test
    fun `suspend resume skips committed edges without duplicating graph state`(@TempDir dir: Path) = runSuspendIO {
        val input = dir.resolve("graph-suspend.graphml")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphSuspendOperations()

        SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(input), graph, options,
        ).status.shouldBeEqualTo(GraphIoStatus.FAILED)
        store.load(KEY)?.edgesProcessed.shouldBeEqualTo(1L)

        Files.writeString(input, fixture("n2"))
        val resumed = SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(input),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        resumed.status.shouldBeEqualTo(GraphIoStatus.COMPLETED)
        resumed.verticesCreated.shouldBeEqualTo(0L)
        resumed.edgesCreated.shouldBeEqualTo(1L)
        store.load(KEY).shouldBeEqualTo(null)
    }

    @Test
    fun `suspend cancellation does not persist a failed checkpoint`(@TempDir dir: Path) = runSuspendIO {
        val input = dir.resolve("graph-cancel.graphml")
        Files.writeString(input, fixture("n2", includeSecondEdge = false))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val cancellation = CancellationException("graphml-import-cancelled")
        val operations = mockk<GraphSuspendOperations>()
        coEvery { operations.createVertices("Person", any()) } throws cancellation

        val thrown = assertFailsWith<CancellationException> {
            SuspendGraphMlBulkImporter().importGraphSuspending(
                GraphImportSource.PathSource(input), operations, options,
            )
        }

        thrown.message.shouldBeEqualTo(cancellation.message)
        store.load(KEY)?.phase.shouldBeEqualTo(GraphImportCheckpointPhase.DISCOVERED)
        store.load(KEY)?.failureBoundary.shouldBeEqualTo(null)
    }

    @Test
    fun `virtual thread resume uses the same checkpoint lifecycle`(@TempDir dir: Path) {
        val input = dir.resolve("graph-vt.graphml")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphOperations()
        val importer = GraphMlVirtualThreadBulkImporter()

        importer.importGraphAsync(GraphImportSource.PathSource(input), graph, options).join()
            .status.shouldBeEqualTo(GraphIoStatus.FAILED)
        Files.writeString(input, fixture("n2"))
        val resumed = importer.importGraphAsync(
            GraphImportSource.PathSource(input),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        ).join()

        resumed.status.shouldBeEqualTo(GraphIoStatus.COMPLETED)
        resumed.verticesCreated.shouldBeEqualTo(0L)
        resumed.edgesCreated.shouldBeEqualTo(1L)
        store.load(KEY).shouldBeEqualTo(null)
    }

    @Test
    fun `sync edge buffer failure checkpoints already flushed vertices`(@TempDir dir: Path) {
        val input = dir.resolve("graph-overflow.graphml")
        Files.writeString(input, fixture("overflow"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store).copyWithCheckpointSourceIdentity(maxEdgeBufferSize = 1)
        val graph = TinkerGraphOperations()

        GraphMlBulkImporter().importGraph(GraphImportSource.PathSource(input), graph, options)
            .status.shouldBeEqualTo(GraphIoStatus.FAILED)
        store.load(KEY)?.verticesProcessed.shouldBeEqualTo(2L)
        store.load(KEY)?.edgesProcessed.shouldBeEqualTo(0L)

        Files.writeString(input, fixture("n2", includeSecondEdge = false))
        val resumed = GraphMlBulkImporter().importGraph(
            GraphImportSource.PathSource(input),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        resumed.status.shouldBeEqualTo(GraphIoStatus.COMPLETED)
        resumed.verticesCreated.shouldBeEqualTo(0L)
        resumed.edgesCreated.shouldBeEqualTo(1L)
        store.load(KEY).shouldBeEqualTo(null)
    }

    @Test
    fun `suspend edge buffer failure checkpoints already flushed vertices`(@TempDir dir: Path) = runSuspendIO {
        val input = dir.resolve("graph-overflow-suspend.graphml")
        Files.writeString(input, fixture("overflow"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store).copyWithCheckpointSourceIdentity(maxEdgeBufferSize = 1)
        val graph = TinkerGraphSuspendOperations()

        SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(input), graph, options,
        ).status.shouldBeEqualTo(GraphIoStatus.FAILED)
        store.load(KEY)?.verticesProcessed.shouldBeEqualTo(2L)
        store.load(KEY)?.edgesProcessed.shouldBeEqualTo(0L)

        Files.writeString(input, fixture("n2", includeSecondEdge = false))
        val resumed = SuspendGraphMlBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(input),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        resumed.status.shouldBeEqualTo(GraphIoStatus.COMPLETED)
        resumed.verticesCreated.shouldBeEqualTo(0L)
        resumed.edgesCreated.shouldBeEqualTo(1L)
        store.load(KEY).shouldBeEqualTo(null)
    }

    private fun options(store: InMemoryGraphImportCheckpointStore) = GraphImportOptions(
        batchSize = 2,
        checkpointStore = store,
        checkpointKey = KEY,
        checkpointSourceIdentity = SOURCE_ID,
    )

    private fun fixture(endpoint: String, includeSecondEdge: Boolean = true): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <graphml xmlns="http://graphml.graphdrawing.org/graphml">
          <key id="label" for="all" attr.name="label" attr.type="string"/>
          <graph id="G" edgedefault="directed">
            <node id="n1"><data key="label">Person</data></node>
            <node id="n2"><data key="label">Person</data></node>
            <edge id="e1" source="n1" target="n2"/>
            ${if (includeSecondEdge) "<edge id=\"e2\" source=\"n1\" target=\"$endpoint\"/>" else ""}
          </graph>
        </graphml>
    """.trimIndent()

    private companion object {
        const val KEY = "graphml-checkpoint"
        const val SOURCE_ID = "graphml-source-v1"
    }
}

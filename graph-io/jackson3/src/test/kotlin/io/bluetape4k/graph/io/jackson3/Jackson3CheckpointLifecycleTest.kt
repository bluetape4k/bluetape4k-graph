package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointPhase
import io.bluetape4k.graph.io.checkpoint.InMemoryGraphImportCheckpointStore
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.copyWithCheckpointSourceIdentity
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Jackson3CheckpointLifecycleTest {

    @Test
    fun `sync resume skips committed edges without duplicating graph state`(@TempDir dir: Path) {
        val input = dir.resolve("graph.ndjson")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphOperations()

        Jackson3NdJsonBulkImporter().importGraph(GraphImportSource.PathSource(input), graph, options).status
            .shouldBeEqualTo(GraphIoStatus.FAILED)
        store.load(KEY)?.phase.shouldBeEqualTo(GraphImportCheckpointPhase.FAILED)
        store.load(KEY)?.edgesProcessed.shouldBeEqualTo(1L)

        Files.writeString(input, fixture("v2"))
        val resumed = Jackson3NdJsonBulkImporter().importGraph(
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
        val input = dir.resolve("graph-suspend.ndjson")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphSuspendOperations()

        SuspendJackson3NdJsonBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(input), graph, options,
        ).status.shouldBeEqualTo(GraphIoStatus.FAILED)
        store.load(KEY)?.edgesProcessed.shouldBeEqualTo(1L)

        Files.writeString(input, fixture("v2"))
        val resumed = SuspendJackson3NdJsonBulkImporter().importGraphSuspending(
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
    fun `virtual thread resume uses the same checkpoint lifecycle`(@TempDir dir: Path) {
        val input = dir.resolve("graph-vt.ndjson")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphOperations()
        val importer = Jackson3NdJsonVirtualThreadBulkImporter()

        importer.importGraphAsync(GraphImportSource.PathSource(input), graph, options).join()
            .status.shouldBeEqualTo(GraphIoStatus.FAILED)
        Files.writeString(input, fixture("v2"))
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

    private fun options(store: InMemoryGraphImportCheckpointStore) = GraphImportOptions(
        batchSize = 2,
        checkpointStore = store,
        checkpointKey = KEY,
        checkpointSourceIdentity = SOURCE_ID,
    )

    private fun fixture(endpoint: String): String = buildString {
        appendLine("""{"type":"vertex","id":"v1","label":"Person","properties":{}}""")
        appendLine("""{"type":"vertex","id":"v2","label":"Person","properties":{}}""")
        appendLine("""{"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}""")
        appendLine("""{"type":"edge","id":"e2","label":"KNOWS","from":"v1","to":"$endpoint","properties":{}}""")
    }

    private companion object {
        const val KEY = "jackson3-checkpoint"
        const val SOURCE_ID = "jackson3-source-v1"
    }
}

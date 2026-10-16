package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointConflictException
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointPhase
import io.bluetape4k.graph.io.checkpoint.InMemoryGraphImportCheckpointStore
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.NdJsonReadOptions
import io.bluetape4k.graph.io.options.copyWithCheckpointSourceIdentity
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Jackson2CheckpointLifecycleTest {

    private companion object: KLogging() {
        const val KEY = "jackson2-checkpoint"
        const val SOURCE_ID = "jackson2-source-v1"
    }

    @Test
    fun `sync resume skips committed edges without duplicating graph state`(@TempDir dir: Path) {
        val input = dir.resolve("graph.ndjson")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphOperations()

        Jackson2NdJsonBulkImporter().importGraph(input.asSource(), graph, options)
            .status shouldBeEqualTo GraphIoStatus.FAILED

        store.load(KEY)?.phase shouldBeEqualTo GraphImportCheckpointPhase.FAILED
        store.load(KEY)?.edgesProcessed shouldBeEqualTo 1L

        Files.writeString(input, fixture("v2"))
        val resumed = Jackson2NdJsonBulkImporter()
            .importGraph(
                input.asSource(),
                graph,
                options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
            )

        log.debug { "resumed=$resumed" }
        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load(KEY).shouldBeNull()
    }

    @Test
    fun `suspend resume skips committed edges without duplicating graph state`(@TempDir dir: Path) = runSuspendIO {
        val input = dir.resolve("graph-suspend.ndjson")
        Files.writeString(input, fixture("missing"))

        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphSuspendOperations()

        SuspendJackson2NdJsonBulkImporter()
            .importGraphSuspending(
                input.asSource(), graph, options,
            )
            .status shouldBeEqualTo GraphIoStatus.FAILED
        store.load(KEY)?.edgesProcessed shouldBeEqualTo 1L

        Files.writeString(input, fixture("v2"))
        val resumed = SuspendJackson2NdJsonBulkImporter().importGraphSuspending(
            input.asSource(),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        log.debug { "resumed=$resumed" }
        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load(KEY).shouldBeNull()
    }

    @Test
    fun `suspend cancellation does not persist a failed checkpoint`(@TempDir dir: Path) = runTest {
        val input = dir.resolve("graph-cancel.ndjson")
        Files.writeString(input, """{"type":"vertex","id":"v1","label":"Person","properties":{}}""")
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val cancellation = CancellationException("jackson2-import-cancelled")
        val operations = mockk<GraphSuspendOperations>()

        coEvery { operations.createVertices("Person", any()) } throws cancellation

        val thrown = assertFailsWith<CancellationException> {
            SuspendJackson2NdJsonBulkImporter().importGraphSuspending(
                input.asSource(), operations, options,
            )
        }

        log.debug { "thrown=$thrown" }
        thrown.message shouldBeEqualTo cancellation.message
        store.load(KEY)?.phase shouldBeEqualTo GraphImportCheckpointPhase.DISCOVERED
        store.load(KEY)?.failureBoundary.shouldBeNull()
    }

    @Test
    fun `virtual thread resume uses the same checkpoint lifecycle`(@TempDir dir: Path) {
        val input = dir.resolve("graph-vt.ndjson")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)
        val graph = TinkerGraphOperations()
        val importer = Jackson2NdJsonVirtualThreadBulkImporter()

        importer.importGraphAsync(input.asSource(), graph, options).join()
            .status shouldBeEqualTo GraphIoStatus.FAILED

        Files.writeString(input, fixture("v2"))
        val resumed = importer.importGraphAsync(
            input.asSource(),
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        ).join()

        log.debug { "resumed=$resumed" }
        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load(KEY).shouldBeNull()
    }

    @Test
    fun `다른 줄 길이 상한으로 checkpoint를 재개할 수 없다`(@TempDir dir: Path) = runSuspendIO {
        val input = dir.resolve("graph-line-limit.ndjson")
        Files.writeString(input, fixture("missing"))
        val store = InMemoryGraphImportCheckpointStore()
        val options = options(store)

        Jackson2NdJsonBulkImporter(NdJsonReadOptions(maxLineChars = 1_024))
            .importGraph(
                input.asSource(),
                TinkerGraphOperations(),
                options
            )
            .status.shouldBeEqualTo(GraphIoStatus.FAILED)

        Files.writeString(input, fixture("v2"))

        assertFailsWith<GraphImportCheckpointConflictException> {
            SuspendJackson2NdJsonBulkImporter(NdJsonReadOptions(maxLineChars = 2_048))
                .importGraphSuspending(
                    input.asSource(),
                    TinkerGraphSuspendOperations(),
                    options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
                )
        }
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
}

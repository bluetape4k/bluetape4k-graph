package io.bluetape4k.graph.io.okio

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointPhase
import io.bluetape4k.graph.io.checkpoint.InMemoryGraphImportCheckpointStore
import io.bluetape4k.graph.io.okio.coroutines.SuspendGraphIoOkioBulkAdapter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.copyWithCheckpointSourceIdentity
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test

class OkioCheckpointLifecycleTest: AbstractOkioTest() {

    private companion object: KLogging() {
        const val KEY = "okio-checkpoint"
        const val SOURCE_ID = "okio-source-v1"
    }

    @Test
    fun `facade forwards checkpoint options to delegated NDJSON importer`() {
        val path = "/checkpoint.ndjson".toPath()
        val source = OkioGraphImportSource.PathSource(path, fakeFs)
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 2,
            checkpointStore = store,
            checkpointKey = KEY,
            checkpointSourceIdentity = SOURCE_ID,
        )
        val importer = OkioGraphBulkImporter.Default
        val graph = TinkerGraphOperations.Default

        write(path, fixture("missing"))

        val failed = importer.importGraph(
            source,
            GraphIoFormat.NDJSON_JACKSON2,
            graph,
            options
        )
        log.debug { "failed=$failed" }
        failed.status shouldBeEqualTo GraphIoStatus.FAILED

        store.load(KEY)?.phase shouldBeEqualTo GraphImportCheckpointPhase.FAILED
        store.load(KEY)?.edgesProcessed shouldBeEqualTo 1L

        write(path, fixture("v2"))
        val resumed = importer.importGraph(
            source,
            GraphIoFormat.NDJSON_JACKSON2,
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
    fun `suspend facade forwards checkpoint options to delegated importer`() = runSuspendIO {
        val path = "/checkpoint-suspend.ndjson".toPath()
        val source = OkioGraphImportSource.PathSource(path, fakeFs)
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 2,
            checkpointStore = store,
            checkpointKey = "okio-suspend-checkpoint",
            checkpointSourceIdentity = SOURCE_ID,
        )
        val importer = SuspendGraphIoOkioBulkAdapter.Default
        val graph = TinkerGraphOperations.Default

        write(path, fixture("missing"))
        val failed = importer.importGraphAwait(
            source,
            GraphIoFormat.NDJSON_JACKSON2,
            graph,
            options
        )
        log.debug { "failed=$failed" }
        failed.status shouldBeEqualTo GraphIoStatus.FAILED

        store.load("okio-suspend-checkpoint")?.edgesProcessed shouldBeEqualTo 1L

        write(path, fixture("v2"))
        val resumed = importer.importGraphAwait(
            source,
            GraphIoFormat.NDJSON_JACKSON2,
            graph,
            options.copyWithCheckpointSourceIdentity(
                checkpointKey = "okio-suspend-checkpoint",
                resumeFromCheckpoint = true,
            ),
        )

        log.debug { "resumed=$resumed" }
        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load("okio-suspend-checkpoint").shouldBeNull()
    }

    private fun write(path: okio.Path, value: String) {
        fakeFs.write(path) { writeUtf8(value) }
    }

    private fun fixture(endpoint: String): String = buildString {
        appendLine("""{"type":"vertex","id":"v1","label":"Person","properties":{}}""")
        appendLine("""{"type":"vertex","id":"v2","label":"Person","properties":{}}""")
        appendLine("""{"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}""")
        appendLine("""{"type":"edge","id":"e2","label":"KNOWS","from":"v1","to":"$endpoint","properties":{}}""")
    }
}

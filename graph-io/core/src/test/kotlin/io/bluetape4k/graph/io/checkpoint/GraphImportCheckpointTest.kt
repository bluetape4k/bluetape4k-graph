package io.bluetape4k.graph.io.checkpoint

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.copyWithCheckpointSourceIdentity
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.model.GraphElementId
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

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

    @Test
    fun `session saves mapping and restores a resumable vertex boundary`() {
        val store = InMemoryGraphImportCheckpointStore()
        val firstMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.FAIL)
        firstMap.putFirstOrFail("external-1", GraphElementId("backend-101"))
        val first = GraphImportCheckpointSession(
            format = GraphIoFormat.NDJSON_JACKSON3,
            sourceIdentity = "sha256:source-v1",
            options = GraphImportOptions(checkpointStore = store, checkpointKey = "job-1"),
            idMap = firstMap,
        )

        first.verticesCommitted(1)
        first.failed("VERTICES")
        val resumedMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.FAIL)
        val resumed = GraphImportCheckpointSession(
            format = GraphIoFormat.NDJSON_JACKSON3,
            sourceIdentity = "sha256:source-v1",
            options = GraphImportOptions(
                checkpointStore = store,
                checkpointKey = "job-1",
                resumeFromCheckpoint = true,
            ),
            idMap = resumedMap,
        )

        resumed.shouldSkipVertex(1) shouldBeEqualTo true
        resumed.shouldSkipVertex(2) shouldBeEqualTo false
        resumedMap.resolve("external-1") shouldBeEqualTo GraphElementId("backend-101")
    }

    @Test
    fun `resume requires an existing compatible checkpoint`() {
        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointSession(
                format = GraphIoFormat.CSV,
                sourceIdentity = "sha256:missing",
                options = GraphImportOptions(
                    checkpointStore = InMemoryGraphImportCheckpointStore(),
                    checkpointKey = "missing",
                    resumeFromCheckpoint = true,
                ),
                idMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.FAIL),
            )
        }
    }

    @Test
    fun `resume rejects a changed source identity`() {
        val store = InMemoryGraphImportCheckpointStore()
        store.save(
            "job-2",
            checkpoint.copy(sourceIdentity = "sha256:original", verticesProcessed = 0),
        )

        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointSession(
                format = GraphIoFormat.NDJSON_JACKSON3,
                sourceIdentity = "sha256:changed",
                options = GraphImportOptions(
                    checkpointStore = store,
                    checkpointKey = "job-2",
                    resumeFromCheckpoint = true,
                ),
                idMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.FAIL),
            )
        }
    }

    @Test
    fun `resume rejects an empty mapping for committed vertices`() {
        val store = InMemoryGraphImportCheckpointStore()
        store.save(
            "job-3",
            checkpoint.copy(
                sourceIdentity = "sha256:source-v3",
                verticesProcessed = 1,
                externalIdMappingState = "v1",
            ),
        )

        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointSession(
                format = GraphIoFormat.NDJSON_JACKSON3,
                sourceIdentity = "sha256:source-v3",
                options = GraphImportOptions(
                    checkpointStore = store,
                    checkpointKey = "job-3",
                    resumeFromCheckpoint = true,
                ),
                idMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.FAIL),
            )
        }
    }

    @Test
    fun `resume rejects changed import semantics`() {
        val store = InMemoryGraphImportCheckpointStore()
        val initial = GraphImportOptions(checkpointStore = store, checkpointKey = "job-4")
        val session = GraphImportCheckpointSession(
            format = GraphIoFormat.CSV,
            sourceIdentity = "sha256:source-v4",
            options = initial,
            idMap = GraphIoExternalIdMap(initial.onDuplicateVertexId),
        )
        session.failed("VERTICES")

        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointSession(
                format = GraphIoFormat.CSV,
                sourceIdentity = "sha256:source-v4",
                options = initial.copy(
                    onDuplicateVertexId = io.bluetape4k.graph.io.options.DuplicateVertexPolicy.SKIP,
                    resumeFromCheckpoint = true,
                ),
                idMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.SKIP),
            )
        }
    }

    @Test
    fun `new attempt does not overwrite an existing checkpoint`() {
        val store = InMemoryGraphImportCheckpointStore()
        store.save("job-5", checkpoint)

        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointSession(
                format = GraphIoFormat.NDJSON_JACKSON3,
                sourceIdentity = checkpoint.sourceIdentity,
                options = GraphImportOptions(checkpointStore = store, checkpointKey = "job-5"),
                idMap = GraphIoExternalIdMap(io.bluetape4k.graph.io.options.DuplicateVertexPolicy.FAIL),
            )
        }
    }

    @Test
    fun `active claim cannot be replaced on the same thread and close releases it`() {
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(checkpointStore = store, checkpointKey = "job-claim")
        val first = GraphImportCheckpointSession(
            format = GraphIoFormat.CSV,
            sourceIdentity = "sha256:claim",
            options = options,
            idMap = GraphIoExternalIdMap(options.onDuplicateVertexId),
        )

        assertFailsWith<GraphImportCheckpointConflictException> {
            GraphImportCheckpointSession(
                format = GraphIoFormat.CSV,
                sourceIdentity = "sha256:claim",
                options = options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
                idMap = GraphIoExternalIdMap(options.onDuplicateVertexId),
            )
        }

        first.close()
        val resumed = GraphImportCheckpointSession(
            format = GraphIoFormat.CSV,
            sourceIdentity = "sha256:claim",
            options = options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
            idMap = GraphIoExternalIdMap(options.onDuplicateVertexId),
        )
        resumed.completed()
    }

    @Test
    fun `progress save rejects a checkpoint owned by another attempt`() {
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(checkpointStore = store, checkpointKey = "job-fence")
        val session = GraphImportCheckpointSession(
            format = GraphIoFormat.CSV,
            sourceIdentity = "sha256:fence",
            options = options,
            idMap = GraphIoExternalIdMap(options.onDuplicateVertexId),
        )
        val current = requireNotNull(store.load("job-fence"))
        store.save("job-fence", current.withMetadata(current.importOptionsIdentity, "other-attempt"))

        assertFailsWith<GraphImportCheckpointConflictException> {
            session.verticesCommitted(1)
        }
        session.close()
    }

    @Test
    fun `checkpoint mode uses single-record writer boundaries`() {
        val store = InMemoryGraphImportCheckpointStore()
        GraphImportOptions(
            batchSize = 100,
            checkpointStore = store,
            checkpointKey = "job-6",
        ).writeBatchSize shouldBeEqualTo 1
        GraphImportOptions(batchSize = 100).writeBatchSize shouldBeEqualTo 100
    }

    @Test
    fun `stream checkpoint requires an explicit stable identity`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportCheckpointIdentity.resolve(
                GraphImportOptions(
                    checkpointStore = InMemoryGraphImportCheckpointStore(),
                    checkpointKey = "job-7",
                ),
                io.bluetape4k.graph.io.source.GraphImportSource.InputStreamSource(ByteArrayInputStream(byteArrayOf())),
            )
        }
    }
}

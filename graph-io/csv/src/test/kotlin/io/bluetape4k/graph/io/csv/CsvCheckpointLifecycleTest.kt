package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.checkpoint.GraphImportCheckpointPhase
import io.bluetape4k.graph.io.checkpoint.InMemoryGraphImportCheckpointStore
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.copyWithCheckpointSourceIdentity
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CsvCheckpointLifecycleTest {

    @Test
    fun `failed edge phase resumes without duplicating vertices`(@TempDir dir: Path) {
        val vertices = dir.resolve("vertices.csv")
        val edges = dir.resolve("edges.csv")
        Files.writeString(vertices, "id,label\nv1,Person\nv2,Person\n")
        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,missing\n")
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(vertices),
            GraphImportSource.PathSource(edges),
        )
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 1,
            checkpointStore = store,
            checkpointKey = "csv-job",
            checkpointSourceIdentity = "csv-source-v1",
        )
        val graph = TinkerGraphOperations()

        CsvGraphBulkImporter().importGraph(source, graph, options).status shouldBeEqualTo GraphIoStatus.FAILED
        store.load("csv-job")?.phase shouldBeEqualTo GraphImportCheckpointPhase.FAILED
        store.load("csv-job")?.verticesProcessed shouldBeEqualTo 2L

        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,v2\n")
        val resumed = CsvGraphBulkImporter().importGraph(
            source,
            graph,
            options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load("csv-job") shouldBeEqualTo null
    }

    @Test
    fun `suspend failed edge phase resumes without duplicating vertices`(@TempDir dir: Path) = runSuspendIO {
        val vertices = dir.resolve("vertices-suspend.csv")
        val edges = dir.resolve("edges-suspend.csv")
        Files.writeString(vertices, "id,label\nv1,Person\nv2,Person\n")
        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,missing\n")
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(vertices),
            GraphImportSource.PathSource(edges),
        )
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 2,
            checkpointStore = store,
            checkpointKey = "csv-suspend-job",
            checkpointSourceIdentity = "csv-source-v1",
        )
        val graph = TinkerGraphSuspendOperations()

        SuspendCsvGraphBulkImporter().importGraphSuspending(source, graph, options)
            .status shouldBeEqualTo GraphIoStatus.FAILED
        store.load("csv-suspend-job")?.verticesProcessed shouldBeEqualTo 2L

        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,v2\n")
        val resumed = SuspendCsvGraphBulkImporter().importGraphSuspending(
            source, graph, options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        )

        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load("csv-suspend-job") shouldBeEqualTo null
    }

    @Test
    fun `suspend cancellation does not persist a failed checkpoint`(@TempDir dir: Path) = runSuspendIO {
        val vertices = dir.resolve("vertices-cancel.csv")
        val edges = dir.resolve("edges-cancel.csv")
        Files.writeString(vertices, "id,label\nv1,Person\n")
        Files.writeString(edges, "id,label,from,to\n")
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(vertices),
            GraphImportSource.PathSource(edges),
        )
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 1,
            checkpointStore = store,
            checkpointKey = "csv-cancel-job",
            checkpointSourceIdentity = "csv-cancel-source",
        )
        val cancellation = CancellationException("csv-import-cancelled")
        val operations = mockk<GraphSuspendOperations>()
        coEvery { operations.createVertices("Person", any()) } throws cancellation

        val thrown = assertFailsWith<CancellationException> {
            SuspendCsvGraphBulkImporter().importGraphSuspending(source, operations, options)
        }

        thrown.message shouldBeEqualTo cancellation.message
        store.load("csv-cancel-job")?.phase shouldBeEqualTo GraphImportCheckpointPhase.DISCOVERED
        store.load("csv-cancel-job")?.failureBoundary shouldBeEqualTo null
    }

    @Test
    fun `active vertex cancellation preserves checkpoint and releases claim`(@TempDir dir: Path) = runTest {
        val vertices = dir.resolve("vertices-active-cancel.csv")
        val edges = dir.resolve("edges-active-cancel.csv")
        Files.writeString(vertices, "id,label\nv1,Person\n")
        Files.writeString(edges, "id,label,from,to\n")
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(vertices),
            GraphImportSource.PathSource(edges),
        )
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 1,
            checkpointStore = store,
            checkpointKey = "csv-active-vertex-cancel-job",
            checkpointSourceIdentity = "csv-active-cancel-source",
        )
        val entered = CompletableDeferred<Unit>()
        val operations = mockk<GraphSuspendOperations>()
        coEvery { operations.createVertices("Person", any()) } coAnswers {
            entered.complete(Unit)
            awaitCancellation()
        }
        val cancellation = CancellationException("csv-active-vertex-cancelled")
        val job = async {
            SuspendCsvGraphBulkImporter().importGraphSuspending(source, operations, options)
        }

        entered.await()
        job.cancel(cancellation)
        val thrown = assertFailsWith<CancellationException> { job.await() }

        thrown.message shouldBeEqualTo cancellation.message
        store.load("csv-active-vertex-cancel-job")?.phase shouldBeEqualTo GraphImportCheckpointPhase.DISCOVERED
        store.load("csv-active-vertex-cancel-job")?.failureBoundary shouldBeEqualTo null
        store.claim("csv-active-vertex-cancel-job", "retry-attempt") shouldBeEqualTo true
        store.release("csv-active-vertex-cancel-job", "retry-attempt")
    }

    @Test
    fun `active edge cancellation preserves committed vertices and releases claim`(@TempDir dir: Path) = runTest {
        val vertices = dir.resolve("vertices-active-edge-cancel.csv")
        val edges = dir.resolve("edges-active-edge-cancel.csv")
        Files.writeString(vertices, "id,label\nv1,Person\nv2,Person\n")
        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,v2\n")
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(vertices),
            GraphImportSource.PathSource(edges),
        )
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 1,
            checkpointStore = store,
            checkpointKey = "csv-active-edge-cancel-job",
            checkpointSourceIdentity = "csv-active-edge-cancel-source",
        )
        val entered = CompletableDeferred<Unit>()
        val operations = mockk<GraphSuspendOperations>()
        var vertexSequence = 0
        coEvery { operations.createVertices("Person", any()) } coAnswers {
            vertexSequence++
            listOf(GraphVertex(GraphElementId.of("v$vertexSequence"), "Person"))
        }
        coEvery { operations.createEdges("KNOWS", any()) } coAnswers {
            entered.complete(Unit)
            awaitCancellation()
        }
        val cancellation = CancellationException("csv-active-edge-cancelled")
        val job = async {
            SuspendCsvGraphBulkImporter().importGraphSuspending(source, operations, options)
        }

        entered.await()
        job.cancel(cancellation)
        val thrown = assertFailsWith<CancellationException> { job.await() }

        thrown.message shouldBeEqualTo cancellation.message
        store.load("csv-active-edge-cancel-job")?.phase shouldBeEqualTo GraphImportCheckpointPhase.VERTICES
        store.load("csv-active-edge-cancel-job")?.verticesProcessed shouldBeEqualTo 2L
        store.load("csv-active-edge-cancel-job")?.failureBoundary shouldBeEqualTo null
        store.claim("csv-active-edge-cancel-job", "retry-attempt") shouldBeEqualTo true
        store.release("csv-active-edge-cancel-job", "retry-attempt")
    }

    @Test
    fun `virtual thread failed edge phase resumes without duplicating vertices`(@TempDir dir: Path) {
        val vertices = dir.resolve("vertices-vt.csv")
        val edges = dir.resolve("edges-vt.csv")
        Files.writeString(vertices, "id,label\nv1,Person\nv2,Person\n")
        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,missing\n")
        val source = CsvGraphImportSource(
            GraphImportSource.PathSource(vertices),
            GraphImportSource.PathSource(edges),
        )
        val store = InMemoryGraphImportCheckpointStore()
        val options = GraphImportOptions(
            batchSize = 2,
            checkpointStore = store,
            checkpointKey = "csv-vt-job",
            checkpointSourceIdentity = "csv-source-v1",
        )
        val graph = TinkerGraphOperations()
        val importer = CsvGraphVirtualThreadBulkImporter()

        importer.importGraphAsync(source, graph, options).join().status shouldBeEqualTo GraphIoStatus.FAILED
        Files.writeString(edges, "id,label,from,to\ne1,KNOWS,v1,v2\n")
        val resumed = importer.importGraphAsync(
            source, graph, options.copyWithCheckpointSourceIdentity(resumeFromCheckpoint = true),
        ).join()

        resumed.status shouldBeEqualTo GraphIoStatus.COMPLETED
        resumed.verticesCreated shouldBeEqualTo 0L
        resumed.edgesCreated shouldBeEqualTo 1L
        store.load("csv-vt-job") shouldBeEqualTo null
    }
}

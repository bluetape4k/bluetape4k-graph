package io.bluetape4k.graph.io.csv

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

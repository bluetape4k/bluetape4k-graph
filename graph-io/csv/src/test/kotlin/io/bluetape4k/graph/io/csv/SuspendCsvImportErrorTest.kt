package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldHaveSize
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class SuspendCsvImportErrorTest {

    companion object : KLoggingChannel()

    private fun importer() = SuspendCsvGraphBulkImporter()
    private fun ops() = TinkerGraphSuspendOperations()

    private fun source(dir: Path, vCsv: String, eCsv: String): CsvGraphImportSource {
        val vFile = dir.resolve("v.csv").also { it.writeText(vCsv) }
        val eFile = dir.resolve("e.csv").also { it.writeText(eCsv) }
        return CsvGraphImportSource(GraphImportSource.PathSource(vFile), GraphImportSource.PathSource(eFile))
    }

    @Test
    fun `blank vertex id causes FAILED status`(@TempDir dir: Path) = runSuspendIO {
        val src = source(
            dir,
            vCsv = "id,label\n,Person\n",
            eCsv = "id,label,from,to\n",
        )
        val report = importer().importGraphSuspending(src, ops(), GraphImportOptions())

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.verticesCreated shouldBeEqualTo 0L
        report.failures shouldHaveSize 1
    }

    @Test
    fun `duplicate vertex id with SKIP policy gives PARTIAL status`(@TempDir dir: Path) = runSuspendIO {
        val src = source(
            dir,
            vCsv = "id,label\nv1,Person\nv1,Person\n",
            eCsv = "id,label,from,to\n",
        )
        val report = importer().importGraphSuspending(
            src,
            ops(),
            GraphImportOptions(onDuplicateVertexId = DuplicateVertexPolicy.SKIP),
        )

        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.verticesCreated shouldBeEqualTo 1L
        report.skippedVertices shouldBeEqualTo 1L
        report.failures shouldHaveSize 1
        report.failures.single().phase shouldBeEqualTo GraphIoPhase.CREATE_VERTEX
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
        report.failures.single().fileRole shouldBeEqualTo GraphIoFileRole.VERTICES
        report.failures.single().message shouldContain "Duplicate vertex externalId skipped"
    }

    @Test
    fun `missing edge endpoint with SKIP_EDGE policy gives PARTIAL status`(@TempDir dir: Path) = runSuspendIO {
        val src = source(
            dir,
            vCsv = "id,label\nv1,Person\n",
            eCsv = "id,label,from,to\n,KNOWS,v1,v999\n",
        )
        val report = importer().importGraphSuspending(
            src,
            ops(),
            GraphImportOptions(onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE),
        )

        report.status shouldBeEqualTo GraphIoStatus.PARTIAL
        report.verticesCreated shouldBeEqualTo 1L
        report.edgesCreated shouldBeEqualTo 0L
        report.skippedEdges shouldBeEqualTo 1L
        report.failures shouldHaveSize 1
        report.failures.single().phase shouldBeEqualTo GraphIoPhase.READ_EDGE
        report.failures.single().severity shouldBeEqualTo GraphIoFailureSeverity.WARN
        report.failures.single().fileRole shouldBeEqualTo GraphIoFileRole.EDGES
        report.failures.single().message shouldContain "Missing endpoint skipped"
    }

    @Test
    fun `missing edge endpoint with FAIL policy gives FAILED status`(@TempDir dir: Path) = runSuspendIO {
        val src = source(
            dir,
            vCsv = "id,label\nv1,Person\n",
            eCsv = "id,label,from,to\n,KNOWS,v1,v999\n",
        )
        val report = importer().importGraphSuspending(
            src,
            ops(),
            GraphImportOptions(onMissingEdgeEndpoint = MissingEndpointPolicy.FAIL),
        )

        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.edgesCreated shouldBeEqualTo 0L
        report.failures shouldHaveSize 1
    }

    @Test
    fun `import with multiple vertices and edges sets correct counts`(@TempDir dir: Path) = runSuspendIO {
        val src = source(
            dir,
            vCsv = "id,label,prop.name\nv1,Person,Alice\nv2,Person,Bob\nv3,Person,Carol\n",
            eCsv = "id,label,from,to\n,KNOWS,v1,v2\n,KNOWS,v2,v3\n",
        )
        val report = importer().importGraphSuspending(src, ops(), GraphImportOptions())

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesRead shouldBeEqualTo 3L
        report.verticesCreated shouldBeEqualTo 3L
        report.edgesRead shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 2L
        report.elapsed.toMillis() shouldBeGreaterThan 0L
    }
}

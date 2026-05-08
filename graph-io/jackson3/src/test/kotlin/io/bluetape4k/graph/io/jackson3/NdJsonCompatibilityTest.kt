package io.bluetape4k.graph.io.jackson3

import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Jackson2 ↔ Jackson3 NDJSON 호환성 테스트.
 *
 * - Jackson3 임포터가 공유 NDJSON fixture를 올바르게 읽는지 검증한다.
 * - Jackson2로 내보낸 NDJSON 파일을 Jackson3 임포터로 읽어도 동일한 데이터가 복원되는지 검증한다.
 * - Jackson3로 내보낸 NDJSON 파일을 Jackson2 임포터로 읽어도 동일한 데이터가 복원되는지 검증한다 (역방향).
 */
class NdJsonCompatibilityTest {

    private val fixture: Path = Paths.get(
        NdJsonCompatibilityTest::class.java.getResource("/fixtures/ndjson/graph.jsonl")!!.toURI()
    )

    @Test
    fun `jackson3 importer reads shared fixture`() {
        val target = TinkerGraphOperations()
        val report = Jackson3NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(fixture),
            target,
            GraphImportOptions(preserveExternalIdProperty = null),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `jackson2 export then jackson3 import gives same logical records`(@TempDir dir: Path) {
        // Step 1: fixture를 Jackson2로 읽어서 TinkerGraph에 로드
        val srcGraph = TinkerGraphOperations()
        Jackson2NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(fixture),
            srcGraph,
            GraphImportOptions(preserveExternalIdProperty = null),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        // Step 2: Jackson2로 export → 임시 파일에 저장
        val exportedFile = dir.resolve("exported-by-jackson2.ndjson")
        Jackson2NdJsonBulkExporter().exportGraph(
            GraphExportSink.PathSink(exportedFile),
            srcGraph,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        // Step 3: Jackson3 임포터로 Jackson2 export 파일을 읽어 동일한 레코드 수 검증
        val targetGraph = TinkerGraphOperations()
        val report = Jackson3NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(exportedFile),
            targetGraph,
            GraphImportOptions(preserveExternalIdProperty = null),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `jackson3 export then jackson2 import gives same logical records`(@TempDir dir: Path) {
        // Step 1: fixture를 Jackson3로 읽어서 TinkerGraph에 로드
        val srcGraph = TinkerGraphOperations()
        Jackson3NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(fixture),
            srcGraph,
            GraphImportOptions(preserveExternalIdProperty = null),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        // Step 2: Jackson3로 export → 임시 파일에 저장
        val exportedFile = dir.resolve("exported-by-jackson3.ndjson")
        Jackson3NdJsonBulkExporter().exportGraph(
            GraphExportSink.PathSink(exportedFile),
            srcGraph,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        // Step 3: Jackson2 임포터로 Jackson3 export 파일을 읽어 동일한 레코드 수 검증 (역방향)
        val targetGraph = TinkerGraphOperations()
        val report = Jackson2NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(exportedFile),
            targetGraph,
            GraphImportOptions(preserveExternalIdProperty = null),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

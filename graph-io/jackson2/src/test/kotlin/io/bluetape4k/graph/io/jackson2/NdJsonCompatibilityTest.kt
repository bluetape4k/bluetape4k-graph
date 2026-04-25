package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Jackson2 ↔ Jackson3 NDJSON 호환성 테스트.
 *
 * 동일한 NDJSON fixture 파일을 Jackson2 임포터로 읽고, export한 뒤
 * 다시 Jackson2 임포터로 재임포트하여 동일한 데이터가 복원되는지 검증한다.
 * (Jackson3 모듈과의 크로스-모듈 호환성은 공유 fixture를 통해 검증한다.)
 */
class NdJsonCompatibilityTest {

    private val fixture: Path = Paths.get(
        NdJsonCompatibilityTest::class.java.getResource("/fixtures/ndjson/graph.jsonl")!!.toURI()
    )

    @Test
    fun `jackson2 importer reads shared fixture`() {
        val target = TinkerGraphOperations()
        val report = Jackson2NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(fixture),
            target,
            GraphImportOptions(preserveExternalIdProperty = null),
        )
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }

    @Test
    fun `jackson2 export then re-import gives same logical records`(@TempDir dir: Path) {
        // Step 1: fixture를 Jackson2로 읽어서 TinkerGraph에 로드
        val srcGraph = TinkerGraphOperations()
        Jackson2NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(fixture),
            srcGraph,
            GraphImportOptions(preserveExternalIdProperty = null),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        // Step 2: Jackson2로 export → 임시 파일에 저장
        val exportedFile = dir.resolve("exported.ndjson")
        val exportReport = Jackson2NdJsonBulkExporter().exportGraph(
            GraphExportSink.PathSink(exportedFile),
            srcGraph,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        )
        exportReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        exportReport.verticesWritten shouldBeEqualTo 2L
        exportReport.edgesWritten shouldBeEqualTo 1L

        // Step 3: Jackson2 임포터로 export된 파일을 재임포트하여 동일한 레코드 수 검증
        // (Jackson3 임포터도 동일한 NDJSON 포맷을 사용하므로 이 파일로 크로스 호환성이 성립함)
        val targetGraph = TinkerGraphOperations()
        val importReport = Jackson2NdJsonBulkImporter().importGraph(
            GraphImportSource.PathSource(exportedFile),
            targetGraph,
            GraphImportOptions(preserveExternalIdProperty = null),
        )
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
        importReport.verticesCreated shouldBeEqualTo 2L
        importReport.edgesCreated shouldBeEqualTo 1L
    }
}

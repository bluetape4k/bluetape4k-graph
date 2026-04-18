package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Jackson2EdgeBufferOverflowTest {

    @Test
    fun `edge buffer overflow causes FAILED status`(@TempDir dir: Path) {
        val ndjson = dir.resolve("graph.ndjson")
        // 간선이 정점보다 먼저 등장 (buffer 누적)
        Files.writeString(ndjson, buildString {
            appendLine("""{"type":"vertex","id":"v1","label":"Person","properties":{}}""")
            // 간선이 v2보다 먼저 등장 → buffer에 쌓임
            appendLine("""{"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{}}""")
            appendLine("""{"type":"edge","id":"e2","label":"KNOWS","from":"v1","to":"v3","properties":{}}""")
            appendLine("""{"type":"edge","id":"e3","label":"KNOWS","from":"v1","to":"v4","properties":{}}""")
        })

        val target = TinkerGraphOperations()
        val importer = Jackson2NdJsonBulkImporter()
        val report = importer.importGraph(
            GraphImportSource.PathSource(ndjson),
            target,
            GraphImportOptions(maxEdgeBufferSize = 2), // buffer 2개까지만 허용
        )
        report.status shouldBeEqualTo GraphIoStatus.FAILED
        report.failures.isNotEmpty().shouldBeTrue()
        report.failures.any { "maxEdgeBufferSize" in it.message }.shouldBeTrue()
        report.verticesCreated shouldBeGreaterThan 0L
    }
}

package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Jackson2SuspendTest {

    @Test
    fun `suspend round trip`(@TempDir dir: Path) = runTest {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphSuspendOperations()
        val alice = src.createVertex("Person", mapOf("name" to "Alice"))
        val bob = src.createVertex("Person", mapOf("name" to "Bob"))
        src.createEdge(alice.id, bob.id, "KNOWS", emptyMap())

        SuspendJackson2NdJsonBulkExporter().exportGraphSuspending(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = SuspendJackson2NdJsonBulkImporter().importGraphSuspending(
            GraphImportSource.PathSource(out),
            TinkerGraphSuspendOperations(),
            GraphImportOptions(),
        )

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

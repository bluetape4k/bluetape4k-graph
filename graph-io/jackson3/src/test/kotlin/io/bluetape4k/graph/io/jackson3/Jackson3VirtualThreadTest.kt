package io.bluetape4k.graph.io.jackson3

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

class Jackson3VirtualThreadTest {

    @Test
    fun `virtual thread round trip`(@TempDir dir: Path) {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphOperations()
        val alice = src.createVertex("Person", mapOf("name" to "Alice"))
        val bob = src.createVertex("Person", mapOf("name" to "Bob"))
        src.createEdge(alice.id, bob.id, "KNOWS", emptyMap())

        Jackson3NdJsonVirtualThreadBulkExporter().exportGraphAsync(
            GraphExportSink.PathSink(out),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).get().status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = Jackson3NdJsonVirtualThreadBulkImporter().importGraphAsync(
            GraphImportSource.PathSource(out),
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

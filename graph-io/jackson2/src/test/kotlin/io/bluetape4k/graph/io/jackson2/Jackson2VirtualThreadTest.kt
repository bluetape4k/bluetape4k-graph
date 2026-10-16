package io.bluetape4k.graph.io.jackson2

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.asSink
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Jackson2VirtualThreadTest {

    companion object: KLogging()

    @Test
    fun `virtual thread round trip`(@TempDir dir: Path) {
        val out = dir.resolve("graph.ndjson")

        val src = TinkerGraphOperations()
        val alice = src.createVertex("Person", "name" to "Alice")
        val bob = src.createVertex("Person", "name" to "Bob")
        src.createEdge(alice.id, bob.id, "KNOWS")

        Jackson2NdJsonVirtualThreadBulkExporter().exportGraphAsync(
            out.asSink(),
            src,
            GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
        ).get().status shouldBeEqualTo GraphIoStatus.COMPLETED

        val report = Jackson2NdJsonVirtualThreadBulkImporter().importGraphAsync(
            out.asSource(),
            TinkerGraphOperations(),
            GraphImportOptions(),
        ).get()

        log.debug { "report=$report" }
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 2L
        report.edgesCreated shouldBeEqualTo 1L
    }
}

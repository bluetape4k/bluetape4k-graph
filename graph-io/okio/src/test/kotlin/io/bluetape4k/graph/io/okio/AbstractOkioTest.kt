package io.bluetape4k.graph.io.okio

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach

abstract class AbstractOkioTest {

    companion object: KLogging()

    protected val fakeFs = FakeFileSystem()

    protected open fun buildSourceGraph(): TinkerGraphOperations {
        val ops = TinkerGraphOperations()
        val alice = ops.createVertex("Person", "name" to "Alice")
        val bob = ops.createVertex("Person", "name" to "Bob")

        ops.createEdge(alice.id, bob.id, "KNOWS")

        return ops
    }

    protected val exportOptions = GraphExportOptions(
        vertexLabels = setOf("Person"),
        edgeLabels = setOf("KNOWS"),
    )

    @AfterEach
    open fun cleanup() {
        fakeFs.checkNoOpenFiles()
    }

    @AfterAll
    fun teardown() {
        fakeFs.close()
    }

    protected fun GraphExportReport.assertSuccessful() {
        status shouldBeEqualTo GraphIoStatus.COMPLETED
        verticesWritten shouldBeEqualTo 2L
        edgesWritten shouldBeEqualTo 1L
    }

    protected fun GraphImportReport.assertSuccessful() {
        status shouldBeEqualTo GraphIoStatus.COMPLETED
        verticesCreated shouldBeEqualTo 2L
        edgesCreated shouldBeEqualTo 1L
    }
}

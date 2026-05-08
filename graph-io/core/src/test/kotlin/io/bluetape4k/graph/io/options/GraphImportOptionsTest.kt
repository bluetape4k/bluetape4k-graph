package io.bluetape4k.graph.io.options

import io.bluetape4k.assertions.invoking
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GraphImportOptionsTest {

    @Test
    fun `default options are valid`() {
        val opts = GraphImportOptions()
        opts.batchSize shouldBeEqualTo 1_000
        opts.maxEdgeBufferSize shouldBeEqualTo 100_000
        opts.onDuplicateVertexId shouldBeEqualTo DuplicateVertexPolicy.FAIL
        opts.onMissingEdgeEndpoint shouldBeEqualTo MissingEndpointPolicy.FAIL
        opts.defaultVertexLabel shouldBeEqualTo "Vertex"
        opts.defaultEdgeLabel shouldBeEqualTo "Edge"
        opts.preserveExternalIdProperty shouldBeEqualTo "_graphIoExternalId"
    }

    @Test
    fun `batchSize must be positive`() {
        invoking { GraphImportOptions(batchSize = 0) } shouldThrow IllegalArgumentException::class
        invoking { GraphImportOptions(batchSize = -1) } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `maxEdgeBufferSize must be positive`() {
        invoking { GraphImportOptions(maxEdgeBufferSize = 0) } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `defaultVertexLabel must not be blank`() {
        invoking { GraphImportOptions(defaultVertexLabel = " ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `defaultEdgeLabel must not be blank`() {
        invoking { GraphImportOptions(defaultEdgeLabel = " ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `preserveExternalIdProperty null disables preservation`() {
        val opts = GraphImportOptions(preserveExternalIdProperty = null)
        opts.preserveExternalIdProperty shouldBeEqualTo null
    }

    @Test
    fun `preserveExternalIdProperty must not be blank when set`() {
        invoking { GraphImportOptions(preserveExternalIdProperty = " ") } shouldThrow IllegalArgumentException::class
    }
}

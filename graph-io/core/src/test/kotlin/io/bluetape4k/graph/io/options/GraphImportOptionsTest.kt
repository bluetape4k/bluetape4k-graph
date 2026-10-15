package io.bluetape4k.graph.io.options

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphImportOptionsTest {

    companion object: KLogging()

    @Test
    fun `default options are valid`() {
        val opts = GraphImportOptions()

        log.debug { "opts=$opts" }
        opts.batchSize shouldBeEqualTo GraphImportOptions.BATCH_SIZE
        opts.maxEdgeBufferSize shouldBeEqualTo GraphImportOptions.MAX_EDGE_BUFFER_SIZE
        opts.onDuplicateVertexId shouldBeEqualTo DuplicateVertexPolicy.FAIL
        opts.onMissingEdgeEndpoint shouldBeEqualTo MissingEndpointPolicy.FAIL
        opts.defaultVertexLabel shouldBeEqualTo "Vertex"
        opts.defaultEdgeLabel shouldBeEqualTo "Edge"
        opts.preserveExternalIdProperty shouldBeEqualTo "_graphIoExternalId"
    }

    @Test
    fun `batchSize must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(batchSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(batchSize = -1)
        }
    }

    @Test
    fun `maxEdgeBufferSize must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(maxEdgeBufferSize = 0)
        }
    }

    @Test
    fun `defaultVertexLabel must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(defaultVertexLabel = " ")
        }
    }

    @Test
    fun `defaultEdgeLabel must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(defaultEdgeLabel = " ")
        }
    }

    @Test
    fun `preserveExternalIdProperty null disables preservation`() {
        val opts = GraphImportOptions(preserveExternalIdProperty = null)
        opts.preserveExternalIdProperty.shouldBeNull()
    }

    @Test
    fun `preserveExternalIdProperty must not be blank when set`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(preserveExternalIdProperty = " ")
        }
    }

    @Test
    fun `resume requires an explicit store and key`() {
        assertFailsWith<IllegalArgumentException> {
            GraphImportOptions(resumeFromCheckpoint = true)
        }
    }
}

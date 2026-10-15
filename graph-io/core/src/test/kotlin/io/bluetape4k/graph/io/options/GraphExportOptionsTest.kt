package io.bluetape4k.graph.io.options

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.repository.DEFAULT_GRAPH_EXPORT_CHUNK_SIZE
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphExportOptionsTest {

    companion object: KLogging()

    @Test
    fun `default options are valid`() {
        val opts = GraphExportOptions()

        log.debug { "opts=$opts" }
        opts.vertexLabels.shouldBeEmpty()
        opts.edgeLabels.shouldBeEmpty()
        opts.includeEmptyProperties.shouldBeTrue()
        opts.exportChunkSize shouldBeEqualTo DEFAULT_GRAPH_EXPORT_CHUNK_SIZE
    }

    @Test
    fun `vertexLabels must not contain blank strings`() {
        assertFailsWith<IllegalArgumentException> {
            GraphExportOptions(vertexLabels = setOf("Person", " "))
        }
    }

    @Test
    fun `edgeLabels must not contain blank strings`() {
        assertFailsWith<IllegalArgumentException> {
            GraphExportOptions(edgeLabels = setOf("KNOWS", ""))
        }
    }

    @Test
    fun `valid labels are accepted`() {
        val opts = GraphExportOptions(
            vertexLabels = setOf("Person", "Company"),
            edgeLabels = setOf("KNOWS", "WORKS_AT"),
        )
        log.debug { "opts=$opts" }
        opts.vertexLabels shouldBeEqualTo setOf("Person", "Company")
        opts.edgeLabels shouldBeEqualTo setOf("KNOWS", "WORKS_AT")
        opts.includeEmptyProperties.shouldBeTrue()
        opts.exportChunkSize shouldBeEqualTo DEFAULT_GRAPH_EXPORT_CHUNK_SIZE
    }
}

package io.bluetape4k.graph.io.options

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GraphExportOptionsTest {

    @Test
    fun `default options are valid`() {
        val opts = GraphExportOptions()
        opts.vertexLabels shouldBeEqualTo emptySet()
        opts.edgeLabels shouldBeEqualTo emptySet()
        opts.includeEmptyProperties shouldBeEqualTo true
    }

    @Test
    fun `vertexLabels must not contain blank strings`() {
        assertFailsWith<IllegalArgumentException> { GraphExportOptions(vertexLabels = setOf("Person", " ")) }
    }

    @Test
    fun `edgeLabels must not contain blank strings`() {
        assertFailsWith<IllegalArgumentException> { GraphExportOptions(edgeLabels = setOf("KNOWS", "")) }
    }

    @Test
    fun `valid labels are accepted`() {
        val opts = GraphExportOptions(
            vertexLabels = setOf("Person", "Company"),
            edgeLabels = setOf("KNOWS", "WORKS_AT"),
        )
        opts.vertexLabels shouldBeEqualTo setOf("Person", "Company")
        opts.edgeLabels shouldBeEqualTo setOf("KNOWS", "WORKS_AT")
    }
}

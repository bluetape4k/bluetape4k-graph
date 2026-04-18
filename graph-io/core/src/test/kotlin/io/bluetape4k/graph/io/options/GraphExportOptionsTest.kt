package io.bluetape4k.graph.io.options

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
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
        { GraphExportOptions(vertexLabels = setOf("Person", " ")) } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `edgeLabels must not contain blank strings`() {
        { GraphExportOptions(edgeLabels = setOf("KNOWS", "")) } shouldThrow IllegalArgumentException::class
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

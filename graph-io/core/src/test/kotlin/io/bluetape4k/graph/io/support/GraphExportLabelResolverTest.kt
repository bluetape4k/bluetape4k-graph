package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.testsupport.FakeGraphOperations
import io.bluetape4k.graph.repository.GraphLabelDiscovery
import org.junit.jupiter.api.Test

class GraphExportLabelResolverTest {

    @Test
    fun `empty labels use backend discovery`() {
        val operations = object : FakeGraphOperations(), GraphLabelDiscovery {
            override fun listVertexLabels(): Set<String> = setOf("Person", "Company")

            override fun listEdgeLabels(): Set<String> = setOf("KNOWS")
        }

        GraphExportOptions().resolveLabels(operations) shouldBeEqualTo
            (setOf("Person", "Company") to setOf("KNOWS"))
    }

    @Test
    fun `empty labels fail explicitly when backend cannot discover`() {
        assertFailsWith<IllegalStateException> {
            GraphExportOptions().resolveLabels(FakeGraphOperations())
        }
    }

    @Test
    fun `explicit labels bypass discovery`() {
        val operations = FakeGraphOperations()
        GraphExportOptions(
            vertexLabels = setOf("Person"),
            edgeLabels = setOf("KNOWS"),
        ).resolveLabels(operations) shouldBeEqualTo
            (setOf("Person") to setOf("KNOWS"))
    }
}

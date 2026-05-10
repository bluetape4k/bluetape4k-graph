package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class BatchEdgeTest {

    @Test
    fun `BatchEdge stores endpoints and properties`() {
        val from = GraphElementId.of("v1")
        val to = GraphElementId.of("v2")
        val edge = BatchEdge(from, to, mapOf("since" to 2024))

        edge.fromId shouldBeEqualTo from
        edge.toId shouldBeEqualTo to
        edge.properties shouldBeEqualTo mapOf("since" to 2024)
    }

    @Test
    fun `BatchEdge defaults properties to empty map`() {
        val edge = BatchEdge(GraphElementId.of("v1"), GraphElementId.of("v2"))

        edge.properties shouldBeEqualTo emptyMap()
    }
}

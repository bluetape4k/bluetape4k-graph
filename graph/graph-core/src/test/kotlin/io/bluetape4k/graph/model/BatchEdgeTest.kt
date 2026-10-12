package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class BatchEdgeTest: AbstractGraphTest() {

    companion object: KLogging()

    @Test
    fun `BatchEdge stores endpoints and properties`() {
        val from = id("v1")
        val to = id("v2")
        val edge = BatchEdge(from, to, mapOf("since" to 2024))

        log.debug { "edge=$edge" }
        edge.fromId shouldBeEqualTo from
        edge.toId shouldBeEqualTo to
        edge.properties shouldBeEqualTo mapOf("since" to 2024)
    }

    @Test
    fun `BatchEdge defaults properties to empty map`() {
        val edge = BatchEdge(GraphElementId.of("v1"), GraphElementId.of("v2"))

        log.debug { "edge=$edge" }
        edge.properties shouldBeEqualTo emptyMap()
    }
}

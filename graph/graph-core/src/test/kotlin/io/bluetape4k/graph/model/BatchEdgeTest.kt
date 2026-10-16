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
        val from = "v1".toGraphElementId()
        val to = "v2".toGraphElementId()
        val edge = BatchEdge(from, to, mapOf("since" to 2024))

        log.debug { "edge=$edge" }
        edge.fromId shouldBeEqualTo from
        edge.toId shouldBeEqualTo to
        edge.properties shouldBeEqualTo mapOf("since" to 2024)
    }

    @Test
    fun `BatchEdge defaults properties to empty map`() {
        val edge = batchEdgeOf("v1", "v2")

        log.debug { "edge=$edge" }
        edge.properties shouldBeEqualTo emptyMap()
    }
}

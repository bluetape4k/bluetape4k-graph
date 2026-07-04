package io.bluetape4k.graph.io.model

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class GraphIoRecordTest {

    @Test
    fun `vertex record requires non-blank externalId`() {
        assertFailsWith<IllegalArgumentException> { GraphIoVertexRecord(externalId = " ", label = "Person") }
    }

    @Test
    fun `edge record requires non-blank label and endpoints`() {
        assertFailsWith<IllegalArgumentException> { GraphIoEdgeRecord(label = " ", fromExternalId = "v1", toExternalId = "v2") }
        assertFailsWith<IllegalArgumentException> { GraphIoEdgeRecord(label = "KNOWS", fromExternalId = " ", toExternalId = "v2") }
        assertFailsWith<IllegalArgumentException> { GraphIoEdgeRecord(label = "KNOWS", fromExternalId = "v1", toExternalId = " ") }
    }

    @Test
    fun `valid records round-trip properties`() {
        val v = GraphIoVertexRecord("v1", "Person", mapOf("name" to "Alice"))
        v.properties["name"] shouldBeEqualTo "Alice"
    }
}

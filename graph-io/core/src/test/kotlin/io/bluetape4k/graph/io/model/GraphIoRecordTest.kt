package io.bluetape4k.graph.io.model

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class GraphIoRecordTest {

    @Test
    fun `vertex record requires non-blank externalId`() {
        { GraphIoVertexRecord(externalId = " ", label = "Person") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `edge record requires non-blank label and endpoints`() {
        { GraphIoEdgeRecord(label = " ", fromExternalId = "v1", toExternalId = "v2") } shouldThrow IllegalArgumentException::class
        { GraphIoEdgeRecord(label = "KNOWS", fromExternalId = " ", toExternalId = "v2") } shouldThrow IllegalArgumentException::class
        { GraphIoEdgeRecord(label = "KNOWS", fromExternalId = "v1", toExternalId = " ") } shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `valid records round-trip properties`() {
        val v = GraphIoVertexRecord("v1", "Person", mapOf("name" to "Alice"))
        v.properties["name"] shouldBeEqualTo "Alice"
    }
}

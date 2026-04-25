package io.bluetape4k.graph.io.jackson2.internal

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class Jackson2EnvelopeCodecTest {

    private val codec = Jackson2EnvelopeCodec()

    @Test
    fun `vertex round trip`() {
        val v = GraphIoVertexRecord("v1", "Person", mapOf("name" to "Alice", "age" to 30))
        val line = codec.writeVertex(v)
        val env = codec.parseLine(line)
        env.type shouldBeEqualTo NdJsonEnvelope.TYPE_VERTEX
        env.id shouldBeEqualTo "v1"
        val rec = codec.toVertex(env, "Default")
        rec.label shouldBeEqualTo "Person"
        rec.properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    fun `edge round trip`() {
        val e = GraphIoEdgeRecord("e1", "KNOWS", "v1", "v2", mapOf("since" to "2024"))
        val line = codec.writeEdge(e)
        val env = codec.parseLine(line)
        env.type shouldBeEqualTo NdJsonEnvelope.TYPE_EDGE
        val rec = codec.toEdge(env, "Default")
        rec.fromExternalId shouldBeEqualTo "v1"
        rec.toExternalId shouldBeEqualTo "v2"
    }
}

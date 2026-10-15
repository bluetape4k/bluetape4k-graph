package io.bluetape4k.graph.io.jackson3.internal

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class Jackson3EnvelopeCodecTest {

    companion object: KLogging()

    private val codec = Jackson3EnvelopeCodec.Default

    @Test
    fun `vertex round trip`() {
        val v = GraphIoVertexRecord("v1", "Person", "name" to "Alice", "age" to 30)
        val line = codec.writeVertex(v)

        val env = codec.parseLine(line)
        env.type shouldBeEqualTo NdJsonEnvelope.TYPE_VERTEX
        env.id shouldBeEqualTo "v1"

        val rec = codec.toVertex(env, "Default")
        log.debug { "vertex record=$rec" }
        rec.label shouldBeEqualTo "Person"
        rec.properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    fun `edge round trip`() {
        val e = GraphIoEdgeRecord("e1", "KNOWS", "v1", "v2", "since" to "2024")
        val line = codec.writeEdge(e)

        val env = codec.parseLine(line)
        env.type shouldBeEqualTo NdJsonEnvelope.TYPE_EDGE

        val rec = codec.toEdge(env, "Default")
        log.debug { "edge record=$rec" }
        rec.fromExternalId shouldBeEqualTo "v1"
        rec.toExternalId shouldBeEqualTo "v2"
    }
}

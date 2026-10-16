package io.bluetape4k.graph.io.jackson2.internal

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.model.edgeRecordOf
import io.bluetape4k.graph.io.model.vertexRecordOf
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class Jackson2EnvelopeCodecTest {

    companion object: KLogging()

    private val codec = Jackson2EnvelopeCodec.Default

    @Test
    fun `vertex round trip`() {
        val v = vertexRecordOf("v1", "Person", "name" to "Alice", "age" to 30)
        val line = codec.writeVertex(v)
        val env = codec.parseLine(line)

        log.debug { "env=$env" }
        env.type shouldBeEqualTo NdJsonEnvelope.TYPE_VERTEX
        env.id shouldBeEqualTo "v1"

        val rec = codec.toVertex(env, "Default")
        log.debug { "rec=$rec" }
        rec.label shouldBeEqualTo "Person"
        rec.properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    fun `edge round trip`() {
        val e = edgeRecordOf("e1", "KNOWS", "v1", "v2", "since" to "2024")
        val line = codec.writeEdge(e)

        val env = codec.parseLine(line)
        log.debug { "env=$env" }
        env.type shouldBeEqualTo NdJsonEnvelope.TYPE_EDGE

        val rec = codec.toEdge(env, "Default")
        log.debug { "rec=$rec" }
        rec.fromExternalId shouldBeEqualTo "v1"
        rec.toExternalId shouldBeEqualTo "v2"
    }
}

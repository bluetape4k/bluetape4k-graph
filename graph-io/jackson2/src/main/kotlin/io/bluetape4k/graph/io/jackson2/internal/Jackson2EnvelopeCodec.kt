package io.bluetape4k.graph.io.jackson2.internal

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.jackson.Jackson
import io.bluetape4k.logging.KLogging

internal class Jackson2EnvelopeCodec(
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper = Jackson.defaultJsonMapper,
) {
    fun parseLine(line: String): NdJsonEnvelope = mapper.readValue(line, NdJsonEnvelope::class.java)

    fun toVertex(env: NdJsonEnvelope, defaultLabel: String): GraphIoVertexRecord {
        require(env.type == NdJsonEnvelope.TYPE_VERTEX) { "Expected vertex envelope" }
        return GraphIoVertexRecord(
            externalId = requireNotNull(env.id) { "vertex envelope missing id" },
            label = env.label?.ifBlank { null } ?: defaultLabel,
            properties = env.properties,
        )
    }

    fun toEdge(env: NdJsonEnvelope, defaultLabel: String): GraphIoEdgeRecord {
        require(env.type == NdJsonEnvelope.TYPE_EDGE) { "Expected edge envelope" }
        return GraphIoEdgeRecord(
            externalId = env.id,
            label = env.label?.ifBlank { null } ?: defaultLabel,
            fromExternalId = requireNotNull(env.from) { "edge envelope missing from" },
            toExternalId = requireNotNull(env.to) { "edge envelope missing to" },
            properties = env.properties,
        )
    }

    fun writeVertex(v: GraphIoVertexRecord): String =
        mapper.writeValueAsString(
            NdJsonEnvelope(NdJsonEnvelope.TYPE_VERTEX, v.externalId, v.label, properties = v.properties)
        )

    fun writeEdge(e: GraphIoEdgeRecord): String =
        mapper.writeValueAsString(
            NdJsonEnvelope(NdJsonEnvelope.TYPE_EDGE, e.externalId, e.label, e.fromExternalId, e.toExternalId, e.properties)
        )

    companion object : KLogging()
}

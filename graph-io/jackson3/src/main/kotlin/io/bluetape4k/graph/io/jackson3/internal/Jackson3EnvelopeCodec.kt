package io.bluetape4k.graph.io.jackson3.internal

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.jackson3.Jackson
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

internal class Jackson3EnvelopeCodec(
    private val mapper: ObjectMapper = Jackson.defaultJsonMapper,
) {
    companion object: KLogging() {
        val Default = Jackson3EnvelopeCodec()
    }

    fun parseLine(line: String): NdJsonEnvelope =
        mapper.readValue<NdJsonEnvelope>(line)

    fun toVertex(env: NdJsonEnvelope, defaultLabel: String): GraphIoVertexRecord {
        require(env.type == NdJsonEnvelope.TYPE_VERTEX) { "Expected vertex envelope" }

        return GraphIoVertexRecord(
            externalId = env.id.requireNotNull { "vertex envelope missing id" },
            label = env.label?.ifBlank { null } ?: defaultLabel,
            properties = env.properties,
        )
    }

    fun toEdge(env: NdJsonEnvelope, defaultLabel: String): GraphIoEdgeRecord {
        require(env.type == NdJsonEnvelope.TYPE_EDGE) { "Expected edge envelope" }

        return GraphIoEdgeRecord(
            externalId = env.id,
            label = env.label?.ifBlank { null } ?: defaultLabel,
            fromExternalId = env.from.requireNotNull { "edge envelope missing from" },
            toExternalId = env.to.requireNotNull { "edge envelope missing to" },
            properties = env.properties,
        )
    }

    fun writeVertex(v: GraphIoVertexRecord): String =
        mapper.writeValueAsString(
            NdJsonEnvelope(
                NdJsonEnvelope.TYPE_VERTEX,
                v.externalId,
                v.label,
                properties = v.properties
            )
        )

    fun writeEdge(e: GraphIoEdgeRecord): String =
        mapper.writeValueAsString(
            NdJsonEnvelope(
                NdJsonEnvelope.TYPE_EDGE,
                e.externalId,
                e.label,
                e.fromExternalId,
                e.toExternalId,
                e.properties
            )
        )
}

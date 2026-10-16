package io.bluetape4k.graph.io.jackson2.internal

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

internal data class NdJsonEnvelope(
    val type: String,
    val id: String? = null,
    val label: String? = null,
    val from: String? = null,
    val to: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L

        const val TYPE_VERTEX = "vertex"
        const val TYPE_EDGE = "edge"

        operator fun invoke(
            type: String,
            id: String? = null,
            label: String? = null,
            from: String? = null,
            to: String? = null,
            vararg params: Pair<String, Any?>,
        ): NdJsonEnvelope {
            type.requireNotBlank("type")

            return NdJsonEnvelope(type, id, label, from, to, params.toMap())
        }
    }
}

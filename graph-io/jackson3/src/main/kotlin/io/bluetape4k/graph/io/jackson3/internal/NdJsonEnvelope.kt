package io.bluetape4k.graph.io.jackson3.internal

import java.io.Serializable

internal data class NdJsonEnvelope(
    val type: String,
    val id: String? = null,
    val label: String? = null,
    val from: String? = null,
    val to: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
        const val TYPE_VERTEX = "vertex"
        const val TYPE_EDGE = "edge"
    }
}

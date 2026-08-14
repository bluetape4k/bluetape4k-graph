package io.bluetape4k.graph.repository

/**
 * Backend capability for discovering labels before an all-label export.
 *
 * Export options use an empty label set to request every label. Backends that
 * cannot enumerate labels must make callers provide explicit labels instead of
 * silently producing an empty export.
 */
interface GraphLabelDiscovery {
    fun listVertexLabels(): Set<String>

    fun listEdgeLabels(): Set<String>
}

/** Coroutine counterpart of [GraphLabelDiscovery]. */
interface GraphSuspendLabelDiscovery {
    suspend fun listVertexLabels(): Set<String>

    suspend fun listEdgeLabels(): Set<String>
}

package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations

/**
 * graph-io importer에서 `GraphImportOptions.batchSize` 단위로 backend batch write를 수행한다.
 *
 * 정점은 레이블별로 모은 뒤 [GraphOperations.createVertices] 호출 결과 순서와 입력 순서를 매칭해
 * 외부 ID 맵을 갱신한다. 간선도 레이블별로 모아 [GraphOperations.createEdges]를 호출한다.
 */
class GraphIoBatchWriter(
    private val operations: GraphOperations,
    private val batchSize: Int,
) {
    private val vertexBuffers = LinkedHashMap<String, MutableList<PendingVertex>>()
    private val edgeBuffers = LinkedHashMap<String, MutableList<BatchEdge>>()

    fun addVertex(
        externalId: String,
        label: String,
        properties: Map<String, Any?>,
        idMap: GraphIoExternalIdMap,
    ): Int {
        val buffer = vertexBuffers.getOrPut(label) { ArrayList(batchSize) }
        buffer += PendingVertex(externalId, properties)
        return if (buffer.size >= batchSize) flushVertices(label, idMap) else 0
    }

    fun flushVertices(idMap: GraphIoExternalIdMap): Int =
        vertexBuffers.keys.toList().sumOf { flushVertices(it, idMap) }

    fun addEdge(
        label: String,
        fromId: GraphElementId,
        toId: GraphElementId,
        properties: Map<String, Any?>,
    ): Int {
        val buffer = edgeBuffers.getOrPut(label) { ArrayList(batchSize) }
        buffer += BatchEdge(fromId, toId, properties)
        return if (buffer.size >= batchSize) flushEdges(label) else 0
    }

    fun flushEdges(): Int =
        edgeBuffers.keys.toList().sumOf { flushEdges(it) }

    private fun flushVertices(label: String, idMap: GraphIoExternalIdMap): Int {
        val buffer = vertexBuffers[label].orEmpty()
        if (buffer.isEmpty()) return 0

        val rows = buffer.toList()
        val created = operations.createVertices(label, rows.map { it.properties })
        require(created.size == rows.size) {
            "createVertices('$label', ...) returned ${created.size} rows for ${rows.size} input rows"
        }
        rows.zip(created).forEach { (pending, vertex) ->
            idMap.put(pending.externalId, vertex.id)
        }
        vertexBuffers[label]?.clear()
        return created.size
    }

    private fun flushEdges(label: String): Int {
        val buffer = edgeBuffers[label].orEmpty()
        if (buffer.isEmpty()) return 0

        val rows = buffer.toList()
        val created = operations.createEdges(label, rows)
        require(created.size == rows.size) {
            "createEdges('$label', ...) returned ${created.size} rows for ${rows.size} input rows"
        }
        edgeBuffers[label]?.clear()
        return created.size
    }

    private data class PendingVertex(
        val externalId: String,
        val properties: Map<String, Any?>,
    )
}

/**
 * suspend graph-io importer용 batch writer.
 */
class SuspendGraphIoBatchWriter(
    private val operations: GraphSuspendOperations,
    private val batchSize: Int,
) {
    private val vertexBuffers = LinkedHashMap<String, MutableList<PendingVertex>>()
    private val edgeBuffers = LinkedHashMap<String, MutableList<BatchEdge>>()

    suspend fun addVertex(
        externalId: String,
        label: String,
        properties: Map<String, Any?>,
        idMap: GraphIoExternalIdMap,
    ): Int {
        val buffer = vertexBuffers.getOrPut(label) { ArrayList(batchSize) }
        buffer += PendingVertex(externalId, properties)
        return if (buffer.size >= batchSize) flushVertices(label, idMap) else 0
    }

    suspend fun flushVertices(idMap: GraphIoExternalIdMap): Int {
        var created = 0
        vertexBuffers.keys.toList().forEach { label ->
            created += flushVertices(label, idMap)
        }
        return created
    }

    suspend fun addEdge(
        label: String,
        fromId: GraphElementId,
        toId: GraphElementId,
        properties: Map<String, Any?>,
    ): Int {
        val buffer = edgeBuffers.getOrPut(label) { ArrayList(batchSize) }
        buffer += BatchEdge(fromId, toId, properties)
        return if (buffer.size >= batchSize) flushEdges(label) else 0
    }

    suspend fun flushEdges(): Int {
        var created = 0
        edgeBuffers.keys.toList().forEach { label ->
            created += flushEdges(label)
        }
        return created
    }

    private suspend fun flushVertices(label: String, idMap: GraphIoExternalIdMap): Int {
        val buffer = vertexBuffers[label].orEmpty()
        if (buffer.isEmpty()) return 0

        val rows = buffer.toList()
        val created = operations.createVertices(label, rows.map { it.properties })
        require(created.size == rows.size) {
            "createVertices('$label', ...) returned ${created.size} rows for ${rows.size} input rows"
        }
        rows.zip(created).forEach { (pending, vertex) ->
            idMap.put(pending.externalId, vertex.id)
        }
        vertexBuffers[label]?.clear()
        return created.size
    }

    private suspend fun flushEdges(label: String): Int {
        val buffer = edgeBuffers[label].orEmpty()
        if (buffer.isEmpty()) return 0

        val rows = buffer.toList()
        val created = operations.createEdges(label, rows)
        require(created.size == rows.size) {
            "createEdges('$label', ...) returned ${created.size} rows for ${rows.size} input rows"
        }
        edgeBuffers[label]?.clear()
        return created.size
    }

    private data class PendingVertex(
        val externalId: String,
        val properties: Map<String, Any?>,
    )
}

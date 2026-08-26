package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import java.util.concurrent.CompletableFuture

/**
 * Virtual Thread chunked read/export surface다.
 *
 * backend의 chunk sequence는 하나의 virtual thread에서 끝까지 소비하고,
 * future 결과는 chunk 경계를 보존한 materialized list로 반환한다. 따라서
 * source traversal은 chunk 단위로 읽지만 반환 시점에는 모든 chunk가 메모리에
 * 존재한다. 진정한 streaming 소비나 조기 close가 필요하면 synchronous
 * `find*ByLabelChunked` cursor 계약을 사용한다.
 */
interface GraphVirtualThreadChunkedOperations {

    fun findVerticesByLabelChunkedAsync(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): CompletableFuture<List<List<GraphVertex>>>

    fun findEdgesByLabelChunkedAsync(
        label: String,
        filter: Map<String, Any?> = emptyMap(),
        chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
    ): CompletableFuture<List<List<GraphEdge>>>
}

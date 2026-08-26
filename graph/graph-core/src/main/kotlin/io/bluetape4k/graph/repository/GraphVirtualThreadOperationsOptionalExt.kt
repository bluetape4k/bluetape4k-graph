package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.model.GraphVertex
import java.util.concurrent.CompletableFuture

private fun <T> unsupportedOptionalSurface(operation: String): CompletableFuture<T> =
    CompletableFuture.failedFuture(
        UnsupportedOperationException(
            "${GraphVirtualThreadOperations::class.simpleName} does not support $operation."
        ),
    )

/** Virtual Thread merge surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.mergeVertexAsync(
    label: String,
    matchProperties: Map<String, Any?>,
    setProperties: Map<String, Any?> = emptyMap(),
): CompletableFuture<GraphVertex> =
    (this as? GraphVirtualThreadMergeOperations)?.mergeVertexAsync(label, matchProperties, setProperties)
        ?: unsupportedOptionalSurface("mergeVertexAsync")

/** Virtual Thread merge surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.mergeEdgeAsync(
    fromId: GraphElementId,
    toId: GraphElementId,
    label: String,
    matchProperties: Map<String, Any?> = emptyMap(),
    setProperties: Map<String, Any?> = emptyMap(),
): CompletableFuture<GraphEdge> =
    (this as? GraphVirtualThreadMergeOperations)?.mergeEdgeAsync(
        fromId,
        toId,
        label,
        matchProperties,
        setProperties,
    ) ?: unsupportedOptionalSurface("mergeEdgeAsync")

/** Virtual Thread schema surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.createIndexAsync(
    label: String,
    property: String,
): CompletableFuture<Unit> =
    (this as? GraphVirtualThreadSchemaManagementOperations)?.createIndexAsync(label, property)
        ?: unsupportedOptionalSurface("createIndexAsync")

/** Virtual Thread schema surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.createUniqueConstraintAsync(
    label: String,
    property: String,
): CompletableFuture<Unit> =
    (this as? GraphVirtualThreadSchemaManagementOperations)?.createUniqueConstraintAsync(label, property)
        ?: unsupportedOptionalSurface("createUniqueConstraintAsync")

/** Virtual Thread schema surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.dropIndexAsync(
    label: String,
    property: String,
): CompletableFuture<Unit> =
    (this as? GraphVirtualThreadSchemaManagementOperations)?.dropIndexAsync(label, property)
        ?: unsupportedOptionalSurface("dropIndexAsync")

/** Virtual Thread schema surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.listIndexesAsync(): CompletableFuture<List<GraphIndex>> =
    (this as? GraphVirtualThreadSchemaManagementOperations)?.listIndexesAsync()
        ?: unsupportedOptionalSurface("listIndexesAsync")

/** Virtual Thread schema surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.listConstraintsAsync(): CompletableFuture<List<GraphConstraint>> =
    (this as? GraphVirtualThreadSchemaManagementOperations)?.listConstraintsAsync()
        ?: unsupportedOptionalSurface("listConstraintsAsync")

/** Virtual Thread transaction surface를 facade에서 조회한다. */
fun <T> GraphVirtualThreadOperations.transactionAsync(
    block: GraphTransactionScope.() -> T,
): CompletableFuture<T> =
    (this as? GraphVirtualThreadTransactionalOperations)?.transactionAsync(block)
        ?: unsupportedOptionalSurface("transactionAsync")

/** Virtual Thread chunked read/export surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.findVerticesByLabelChunkedAsync(
    label: String,
    filter: Map<String, Any?> = emptyMap(),
    chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
): CompletableFuture<List<List<GraphVertex>>> =
    (this as? GraphVirtualThreadChunkedOperations)?.findVerticesByLabelChunkedAsync(label, filter, chunkSize)
        ?: unsupportedOptionalSurface("findVerticesByLabelChunkedAsync")

/** Virtual Thread chunked read/export surface를 facade에서 조회한다. */
fun GraphVirtualThreadOperations.findEdgesByLabelChunkedAsync(
    label: String,
    filter: Map<String, Any?> = emptyMap(),
    chunkSize: Int = DEFAULT_GRAPH_EXPORT_CHUNK_SIZE,
): CompletableFuture<List<List<GraphEdge>>> =
    (this as? GraphVirtualThreadChunkedOperations)?.findEdgesByLabelChunkedAsync(label, filter, chunkSize)
        ?: unsupportedOptionalSurface("findEdgesByLabelChunkedAsync")

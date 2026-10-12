package io.bluetape4k.graph.repository

import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked

/**
 * Default number of records requested by chunk-aware graph export paths.
 */
const val DEFAULT_GRAPH_EXPORT_CHUNK_SIZE: Int = 1_000

internal fun validateGraphExportChunkSize(chunkSize: Int): Int =
    chunkSize.requirePositiveNumber("chunkSize")

internal fun <T> Iterable<T>.asGraphExportChunks(chunkSize: Int): Sequence<List<T>> =
    asSequence().asGraphExportChunks(chunkSize)

internal fun <T> Sequence<T>.asGraphExportChunks(chunkSize: Int): Sequence<List<T>> = chunked(chunkSize)

internal fun <T> Flow<T>.asGraphExportChunks(chunkSize: Int): Flow<List<T>> = chunked(chunkSize)

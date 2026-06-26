package io.bluetape4k.graph.repository

import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Default number of records requested by chunk-aware graph export paths.
 */
const val DEFAULT_GRAPH_EXPORT_CHUNK_SIZE: Int = 1_000

internal fun validateGraphExportChunkSize(chunkSize: Int): Int {
    chunkSize.requirePositiveNumber("chunkSize")
    return chunkSize
}

internal fun <T> Iterable<T>.asGraphExportChunks(chunkSize: Int): Sequence<List<T>> =
    asSequence().asGraphExportChunks(chunkSize)

internal fun <T> Sequence<T>.asGraphExportChunks(chunkSize: Int): Sequence<List<T>> = sequence {
    val validatedChunkSize = validateGraphExportChunkSize(chunkSize)
    val chunk = ArrayList<T>(validatedChunkSize)
    for (record in this@asGraphExportChunks) {
        chunk += record
        if (chunk.size == validatedChunkSize) {
            yield(chunk.toList())
            chunk.clear()
        }
    }
    if (chunk.isNotEmpty()) {
        yield(chunk.toList())
    }
}

internal fun <T> Flow<T>.asGraphExportChunks(chunkSize: Int): Flow<List<T>> = flow {
    val validatedChunkSize = validateGraphExportChunkSize(chunkSize)
    val chunk = ArrayList<T>(validatedChunkSize)
    collect { record ->
        chunk += record
        if (chunk.size == validatedChunkSize) {
            emit(chunk.toList())
            chunk.clear()
        }
    }
    if (chunk.isNotEmpty()) {
        emit(chunk.toList())
    }
}

package io.bluetape4k.graph.falkordb

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.CycleOptions

private val fallbackFailureMarkers = listOf(
    "not supported",
    "not implemented",
    "unsupported",
    "unknown function",
    "unknown procedure",
    "unknown clause",
)

internal fun Throwable.supportsJvmCycleFallback(): Boolean =
    diagnosticText().let { text ->
        fallbackFailureMarkers.any { marker -> marker in text }
    }

internal fun Throwable.asCycleDetectionFailure(
    backend: String,
    options: CycleOptions,
): GraphQueryException =
    this as? GraphQueryException
        ?: GraphQueryException("$backend cycle detection query failed: options=$options", this)

private fun Throwable.diagnosticText(): String =
    generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()

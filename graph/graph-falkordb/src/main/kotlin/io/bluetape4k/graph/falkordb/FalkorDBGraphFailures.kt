package io.bluetape4k.graph.falkordb

/**
 * FalkorDB reports an idempotent delete of an absent graph as an error from the
 * Redis command surface. Only that well-known absence signal is safe to ignore;
 * transport and server failures must remain observable to callers.
 */
internal fun Throwable.isMissingFalkorGraph(): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        cause.message.orEmpty().let { message ->
            message.contains("Invalid graph operation on empty key", ignoreCase = true) ||
                message.contains("graph does not exist", ignoreCase = true) ||
                message.contains("graph not found", ignoreCase = true)
        }
    }

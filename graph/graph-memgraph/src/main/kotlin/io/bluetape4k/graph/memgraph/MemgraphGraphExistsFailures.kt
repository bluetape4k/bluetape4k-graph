package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.GraphQueryException

private val missingDatabaseMarkers = listOf(
    "database not found",
    "database does not exist",
    "databasenotfound",
)

internal fun Throwable.isMissingDatabaseFailure(): Boolean =
    generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()
        .let { text -> missingDatabaseMarkers.any { marker -> marker in text } }

internal fun Throwable.asGraphExistsFailure(backend: String, name: String): GraphQueryException =
    this as? GraphQueryException
        ?: GraphQueryException("$backend graphExists failed: graph=$name", this)

package io.bluetape4k.graph.age

import io.bluetape4k.graph.GraphQueryException
import java.sql.SQLException

private val duplicateGraphSqlStates = setOf(
    "42710", // duplicate_object
    "42P04", // duplicate_database
)

internal fun Throwable.isDuplicateGraphFailure(): Boolean {
    val sqlStateMatches = generateSequence(this) { it.cause }
        .filterIsInstance<SQLException>()
        .mapNotNull { it.sqlState }
        .any { it in duplicateGraphSqlStates }
    if (sqlStateMatches) return true

    val diagnosticText = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()

    return "graph" in diagnosticText && "already exists" in diagnosticText
}

internal fun Throwable.asCreateGraphFailure(name: String): GraphQueryException =
    this as? GraphQueryException
        ?: GraphQueryException("AGE createGraph failed: graph=$name", this)

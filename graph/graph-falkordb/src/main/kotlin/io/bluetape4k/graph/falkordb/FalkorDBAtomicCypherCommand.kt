package io.bluetape4k.graph.falkordb

import com.falkordb.Driver
import com.falkordb.Record
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One parameterized Cypher script executed in one FalkorDB server round-trip.
 *
 * The command is intentionally separate from the unsupported Redis MULTI transaction DSL:
 * the server executes the script as one query and returns its explicit result rows.
 */
data class FalkorDBAtomicCypherCommand(
    val cypher: String,
    val parameters: Map<String, Any?> = emptyMap(),
) {
    init {
        cypher.requireNotBlank("cypher")
        require(';' !in cypher) { "atomic command must contain one parameterized Cypher statement" }
        require(parameters.keys.all { it.matches(PARAMETER_NAME) }) {
            "parameter names must be simple Cypher identifiers"
        }
    }

    companion object {
        private val PARAMETER_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/** Blocking and coroutine adapters for the explicit atomic command surface. */
class FalkorDBAtomicCypherCommandExecutor(
    private val driver: Driver,
    private val graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME,
) {
    init {
        graphName.requireNotBlank("graphName").requireSafeIdentifier("graphName")
    }

    fun execute(command: FalkorDBAtomicCypherCommand): List<Record> =
        driver.graph(graphName).use { graph ->
            @Suppress("UNCHECKED_CAST")
            graph.query(command.cypher, command.parameters as Map<String, Any>).toList()
        }

    suspend fun executeSuspending(command: FalkorDBAtomicCypherCommand): List<Record> =
        withContext(Dispatchers.IO) { execute(command) }
}

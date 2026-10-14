package io.bluetape4k.graph.neo4j

import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Query

internal fun queryOf(cypher: String, parameters: Map<String, Any?> = emptyMap()): Query =
    Query(cypher.requireNotBlank("cypher"), parameters)

internal fun queryOf(cypher: String, vararg params: Pair<String, Any?>): Query =
    Query(cypher.requireNotBlank("cypher"), params.toMap())

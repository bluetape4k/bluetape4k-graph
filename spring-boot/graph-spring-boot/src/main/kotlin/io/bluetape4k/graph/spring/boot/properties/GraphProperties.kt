package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Common bluetape4k Graph auto-configuration properties.
 *
 * `bluetape4k.graph.backend` selects the active backend. Supported values are
 * `tinkergraph`, `neo4j`, `memgraph`, `age`, and `falkordb`. When the value is
 * absent, TinkerGraph is enabled through `matchIfMissing=true`.
 *
 * Example:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.GraphProperties
 *
 * val properties = GraphProperties(backend = "neo4j")
 * check(properties.backend == "neo4j")
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.graph")
data class GraphProperties(
    /** Active backend: `tinkergraph`, `neo4j`, `memgraph`, `age`, or `falkordb`. */
    val backend: String? = null,
)

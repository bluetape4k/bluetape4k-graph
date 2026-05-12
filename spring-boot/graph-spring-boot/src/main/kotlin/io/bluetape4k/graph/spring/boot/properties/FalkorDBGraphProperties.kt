package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * FalkorDB backend connection properties.
 *
 * The backend is active when `bluetape4k.graph.backend=falkordb`. FalkorDB is a
 * Redis module based graph database and is accessed through the jfalkordb
 * driver.
 *
 * Example:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.FalkorDBGraphProperties
 *
 * val properties = FalkorDBGraphProperties(
 *     host = "falkordb.example.test",
 *     port = 6379,
 *     graphName = "recommendations",
 * )
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.falkordb")
data class FalkorDBGraphProperties(
    /** FalkorDB host name or address. */
    val host: String = "localhost",
    /** FalkorDB Redis port. */
    val port: Int = 6379,
    /** Authentication username. Leave blank for unauthenticated connections. */
    val username: String = "",
    /** Authentication password. Leave blank for unauthenticated connections. */
    val password: String = "",
    /** Target graph name. */
    val graphName: String = "bluetape4k",
    /** Whether to register a coroutine [io.bluetape4k.graph.repository.GraphSuspendOperations] bean. */
    val registerSuspend: Boolean = true,
    /** Whether to register a virtual-thread [io.bluetape4k.graph.repository.GraphVirtualThreadOperations] bean. */
    val registerVirtualThread: Boolean = true,
)

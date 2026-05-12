package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TinkerGraph in-memory backend properties.
 *
 * Example:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.TinkerGraphGraphProperties
 *
 * val properties = TinkerGraphGraphProperties(
 *     registerSuspend = true,
 *     registerVirtualThread = true,
 * )
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.tinkergraph")
data class TinkerGraphGraphProperties(
    val registerSuspend: Boolean = true,
    val registerVirtualThread: Boolean = true,
)

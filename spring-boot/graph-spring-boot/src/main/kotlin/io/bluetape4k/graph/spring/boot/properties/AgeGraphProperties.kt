package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Apache AGE backend properties.
 *
 * Example:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.AgeGraphProperties
 *
 * val properties = AgeGraphProperties(
 *     graphName = "tenant_graph",
 *     autoCreateGraph = true,
 * )
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.age")
data class AgeGraphProperties(
    val graphName: String = "bluetape4k_graph",
    val autoCreateGraph: Boolean = true,
    val registerSuspend: Boolean = true,
    val registerVirtualThread: Boolean = true,
)

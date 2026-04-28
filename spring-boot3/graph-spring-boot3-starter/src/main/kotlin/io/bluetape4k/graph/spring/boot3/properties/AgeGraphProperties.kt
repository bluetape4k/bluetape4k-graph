package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Apache AGE 백엔드 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.age")
data class AgeGraphProperties(
    val graphName: String = "bluetape4k_graph",
    val autoCreateGraph: Boolean = true,
    val registerSuspend: Boolean = true,
    val registerVirtualThread: Boolean = true,
)

package io.bluetape4k.graph.spring.boot4.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Apache AGE 백엔드 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.age")
data class AgeGraphProperties(
    var graphName: String = "bluetape4k_graph",
    var autoCreateGraph: Boolean = true,
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)

package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TinkerGraph 인메모리 백엔드 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.tinkergraph")
data class TinkerGraphGraphProperties(
    val registerSuspend: Boolean = true,
    val registerVirtualThread: Boolean = true,
)

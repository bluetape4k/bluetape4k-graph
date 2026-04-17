package io.bluetape4k.graph.spring.boot4.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TinkerGraph 인메모리 백엔드 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.tinkergraph")
data class TinkerGraphGraphProperties(
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)

package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Memgraph 백엔드 연결 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.memgraph")
data class MemgraphGraphProperties(
    var uri: String = "bolt://localhost:7687",
    var username: String = "",
    var password: String = "",
    var database: String = "memgraph",
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)

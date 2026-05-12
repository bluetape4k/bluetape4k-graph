package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Memgraph 백엔드 연결 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.memgraph")
data class MemgraphGraphProperties(
    val uri: String = "bolt://localhost:7687",
    val username: String = "",
    val password: String = "",
    val database: String = "memgraph",
    val registerSuspend: Boolean = true,
    val registerVirtualThread: Boolean = true,
    val connectionTimeoutMillis: Long = 5000L,
    val maxConnectionLifetimeMillis: Long = 3600000L,
)

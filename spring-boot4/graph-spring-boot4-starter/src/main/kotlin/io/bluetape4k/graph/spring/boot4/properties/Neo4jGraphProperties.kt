package io.bluetape4k.graph.spring.boot4.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Neo4j 백엔드 연결 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.neo4j")
data class Neo4jGraphProperties(
    val uri: String = "bolt://localhost:7687",
    val username: String = "neo4j",
    val password: String = "",
    val database: String = "neo4j",
    val registerSuspend: Boolean = true,
    val registerVirtualThread: Boolean = true,
    val connectionTimeoutMillis: Long = 5000L,
    val maxConnectionLifetimeMillis: Long = 3600000L,
)

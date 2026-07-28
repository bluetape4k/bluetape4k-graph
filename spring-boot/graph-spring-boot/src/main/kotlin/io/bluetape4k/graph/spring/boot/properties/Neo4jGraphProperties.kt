package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Neo4j backend connection property.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.Neo4jGraphProperties
 *
 * val properties = Neo4jGraphProperties(
 *     uri = "bolt://neo4j.example.test:7687",
 *     username = "neo4j",
 *     password = "secret",
 *     database = "analytics",
 * )
 * ```
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

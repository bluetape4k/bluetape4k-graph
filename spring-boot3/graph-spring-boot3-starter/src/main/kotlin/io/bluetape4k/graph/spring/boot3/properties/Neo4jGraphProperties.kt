package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Neo4j 백엔드 연결 속성.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.neo4j")
data class Neo4jGraphProperties(
    var uri: String = "bolt://localhost:7687",
    var username: String = "neo4j",
    var password: String = "",
    var database: String = "neo4j",
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)

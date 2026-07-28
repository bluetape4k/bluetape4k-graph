package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * FalkorDB backend connection property.
 *
 * `bluetape4k.graph.backend=falkordb`일 때 backend가 활성화된다. FalkorDB는 Redis module 기반 graph database이며 jfalkordb driver로 접근한다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.FalkorDBGraphProperties
 *
 * val properties = FalkorDBGraphProperties(
 *     host = "falkordb.example.test",
 *     port = 6379,
 *     graphName = "recommendations",
 * )
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.falkordb")
data class FalkorDBGraphProperties(
    /** FalkorDB host 이름 또는 address. */
    val host: String = "localhost",
    /** FalkorDB Redis port. */
    val port: Int = 6379,
    /** 인증 username. 인증 없는 connection은 빈 문자열로 둔다. */
    val username: String = "",
    /** 인증 password. 인증 없는 connection은 빈 문자열로 둔다. */
    val password: String = "",
    /** 대상 graph 이름. */
    val graphName: String = "bluetape4k",
    /** Coroutine [io.bluetape4k.graph.repository.GraphSuspendOperations] bean 등록 여부. */
    val registerSuspend: Boolean = true,
    /** Virtual-thread [io.bluetape4k.graph.repository.GraphVirtualThreadOperations] bean 등록 여부. */
    val registerVirtualThread: Boolean = true,
)

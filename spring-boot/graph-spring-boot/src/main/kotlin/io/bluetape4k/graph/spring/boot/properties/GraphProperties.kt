package io.bluetape4k.graph.spring.boot.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * bluetape4k Graph 공통 auto-configuration property.
 *
 * `bluetape4k.graph.backend`는 active backend를 선택한다. 지원 값은 `tinkergraph`, `neo4j`, `memgraph`, `age`, `falkordb`다. 값이 없으면 `matchIfMissing=true`로 TinkerGraph가 활성화된다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.spring.boot.properties.GraphProperties
 *
 * val properties = GraphProperties(backend = "neo4j")
 * check(properties.backend == "neo4j")
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.graph")
data class GraphProperties(
    /** Active backend 값: `tinkergraph`, `neo4j`, `memgraph`, `age`, `falkordb`. */
    val backend: String? = null,
)

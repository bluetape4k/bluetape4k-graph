package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * bluetape4k Graph AutoConfiguration 공통 속성.
 *
 * `bluetape4k.graph.backend` 속성으로 활성화할 백엔드를 선택한다.
 * 지원 값: `tinkergraph`, `neo4j`, `memgraph`, `age`.
 * 기본값 없음 — 지정하지 않으면 TinkerGraph가 `matchIfMissing=true`로 활성화된다.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph")
data class GraphProperties(
    /** 활성 백엔드: `tinkergraph` | `neo4j` | `memgraph` | `age` */
    var backend: String? = null,
)

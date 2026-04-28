package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * FalkorDB 백엔드 연결 속성.
 *
 * `bluetape4k.graph.backend=falkordb` 일 때 활성화된다.
 * FalkorDB는 Redis 모듈 기반의 그래프 데이터베이스이며, jfalkordb 드라이버를 사용한다.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.falkordb")
data class FalkorDBGraphProperties(
    /** FalkorDB 호스트 주소 */
    val host: String = "localhost",
    /** FalkorDB Redis 포트 번호 */
    val port: Int = 6379,
    /** 인증 사용자명 (비어있으면 인증 없음) */
    val username: String = "",
    /** 인증 비밀번호 (비어있으면 인증 없음) */
    val password: String = "",
    /** 대상 그래프 이름 */
    val graphName: String = "bluetape4k",
    /** 코루틴 suspend 기반 [io.bluetape4k.graph.repository.GraphSuspendOperations] 빈 등록 여부 */
    val registerSuspend: Boolean = true,
    /** Virtual Thread 기반 [io.bluetape4k.graph.repository.GraphVirtualThreadOperations] 빈 등록 여부 */
    val registerVirtualThread: Boolean = true,
)

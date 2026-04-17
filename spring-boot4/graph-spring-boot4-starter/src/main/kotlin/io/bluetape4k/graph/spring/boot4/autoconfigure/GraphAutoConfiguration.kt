package io.bluetape4k.graph.spring.boot4.autoconfigure

import io.bluetape4k.graph.spring.boot4.properties.GraphProperties
import io.bluetape4k.logging.KLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * bluetape4k Graph AutoConfiguration 루트.
 *
 * 백엔드별 AutoConfiguration의 실행 순서를 보장하고 공통 속성(`GraphProperties`)을 노출한다.
 * 이 클래스 자체는 빈을 생성하지 않는다.
 * 백엔드 AutoConfiguration은 `AutoConfiguration.imports`에 개별 등록된다.
 */
@AutoConfiguration(
    before = [
        GraphTinkerGraphAutoConfiguration::class,
        GraphNeo4jAutoConfiguration::class,
        GraphMemgraphAutoConfiguration::class,
        GraphAgeAutoConfiguration::class,
    ],
)
@EnableConfigurationProperties(GraphProperties::class)
class GraphAutoConfiguration {
    companion object : KLogging()
}

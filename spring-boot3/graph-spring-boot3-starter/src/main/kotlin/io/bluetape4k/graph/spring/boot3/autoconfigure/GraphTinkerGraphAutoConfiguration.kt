package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.graph.spring.boot3.properties.TinkerGraphGraphProperties
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * TinkerGraph 인메모리 백엔드 AutoConfiguration.
 *
 * `bluetape4k.graph.backend=tinkergraph` 이거나 속성 미지정 시 활성화된다.
 * 외부 의존성이 없으므로 기본 백엔드로 적합하다.
 */
@AutoConfiguration
@ConditionalOnClass(TinkerGraphOperations::class)
@ConditionalOnProperty(
    prefix = "bluetape4k.graph",
    name = ["backend"],
    havingValue = "tinkergraph",
    matchIfMissing = true,
)
@EnableConfigurationProperties(TinkerGraphGraphProperties::class)
class GraphTinkerGraphAutoConfiguration {

    companion object : KLogging()

    /**
     * TinkerGraph 기반 `GraphOperations` 빈.
     *
     * 사용자가 `GraphOperations` 빈을 직접 등록하면 이 빈은 생성되지 않는다.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(): GraphOperations {
        log.info { "Registering TinkerGraphOperations (in-memory backend)" }
        return TinkerGraphOperations()
    }

    /**
     * TinkerGraph 기반 `GraphSuspendOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.tinkergraph",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(ops: GraphOperations): GraphSuspendOperations {
        val tinkerOps = checkNotNull(ops as? TinkerGraphOperations) {
            "Expected TinkerGraphOperations but got ${ops::class.simpleName}"
        }
        return TinkerGraphSuspendOperations(tinkerOps)
    }

    /**
     * Virtual Thread 기반 `GraphVirtualThreadOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.tinkergraph",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    /**
     * Actuator HealthIndicator — nested class로 격리.
     * Actuator 미사용 앱에서 `NoClassDefFoundError` 방지.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun tinkerGraphHealthIndicator(): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
                org.springframework.boot.actuate.health.Health.up()
                    .withDetail("backend", "tinkergraph").build()
            }
    }
}

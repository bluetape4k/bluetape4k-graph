package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.graph.spring.boot.properties.TinkerGraphGraphProperties
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
 * In-memory TinkerGraph backend auto-configuration.
 *
 * `bluetape4k.graph.backend=tinkergraph`이거나 backend property가 없을 때 활성화된다.
 * TinkerGraph는 외부 service dependency가 없으므로 기본 backend다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.repository.GraphOperations
 * import org.springframework.boot.autoconfigure.SpringBootApplication
 * import org.springframework.boot.runApplication
 *
 * @SpringBootApplication
 * class GraphApplication
 *
 * val context = runApplication<GraphApplication>(
 *     "--bluetape4k.graph.backend=tinkergraph",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(name = ["io.bluetape4k.graph.tinkerpop.TinkerGraphOperations"])
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
     * Application이 자체 graph operations bean을 제공하지 않았을 때 in-memory TinkerGraph operations를 등록한다.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(): TinkerGraphOperations {
        log.info { "Registering TinkerGraphOperations (in-memory backend)" }
        return TinkerGraphOperations()
    }

    /**
     * Suspend 지원이 활성화되면 coroutine 친화적 TinkerGraph operations를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.tinkergraph",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(ops: TinkerGraphOperations): GraphSuspendOperations =
        TinkerGraphSuspendOperations(ops)

    /**
     * 동기 TinkerGraph operations를 기반으로 virtual-thread graph operations를 등록한다.
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
     * Actuator health indicator를 분리해 Actuator를 사용하지 않는 application에서 `NoClassDefFoundError`를 피한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])
    class HealthConfig {

        companion object : KLogging()

        @Bean
        @ConditionalOnMissingBean
        fun tinkerGraphHealthIndicator(): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                org.springframework.boot.health.contributor.Health.up()
                    .withDetail("backend", "tinkergraph").build()
            }
    }
}

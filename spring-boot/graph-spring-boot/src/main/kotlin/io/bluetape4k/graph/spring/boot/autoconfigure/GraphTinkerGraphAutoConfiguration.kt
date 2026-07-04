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
 * Auto-configuration for the in-memory TinkerGraph backend.
 *
 * It is active when `bluetape4k.graph.backend=tinkergraph` or when the backend
 * property is absent. TinkerGraph has no external service dependency, so it is
 * the default backend.
 *
 * Example:
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
     * Registers in-memory TinkerGraph operations when the application has not
     * provided its own graph operations bean.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(): TinkerGraphOperations {
        log.info { "Registering TinkerGraphOperations (in-memory backend)" }
        return TinkerGraphOperations()
    }

    /**
     * Registers coroutine-friendly TinkerGraph operations when suspend support is enabled.
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
     * Registers virtual-thread graph operations backed by the synchronous TinkerGraph operations.
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
     * Isolates the Actuator health indicator so non-Actuator applications avoid
     * `NoClassDefFoundError`.
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

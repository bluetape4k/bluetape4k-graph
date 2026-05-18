package io.bluetape4k.graph.spring.boot.autoconfigure

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot.properties.FalkorDBGraphProperties
import io.bluetape4k.graph.vt.asVirtualThread
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
 * Auto-configuration for the FalkorDB backend.
 *
 * It is active when `bluetape4k.graph.backend=falkordb`. FalkorDB is a Redis
 * module based graph database and uses the jfalkordb [com.falkordb.Driver].
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
 *     "--bluetape4k.graph.backend=falkordb",
 *     "--bluetape4k.graph.falkordb.host=localhost",
 *     "--bluetape4k.graph.falkordb.graph-name=bluetape4k",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(com.falkordb.Driver::class, FalkorDBGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "falkordb")
@EnableConfigurationProperties(FalkorDBGraphProperties::class)
class GraphFalkorDBAutoConfiguration {

    companion object : KLogging()

    /**
     * Creates the FalkorDB driver bean.
     *
     * ## Behavior / Contract
     * - Skips registration when the application already provides a [com.falkordb.Driver] bean.
     * - Connects without authentication when [FalkorDBGraphProperties.username] is blank.
     * - Uses username/password authentication when a username is configured.
     * - Spring owns this auto-created driver and closes it through the bean destroy method.
     */
    @Bean(name = ["falkordbDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(com.falkordb.Driver::class)
    fun falkordbDriver(props: FalkorDBGraphProperties): com.falkordb.Driver {
        log.info { "Creating FalkorDB Driver: host=${props.host}, port=${props.port}" }
        return if (props.username.isBlank()) {
            FalkorDB.driver(props.host, props.port)
        } else {
            FalkorDB.driver(props.host, props.port, props.username, props.password)
        }
    }

    /**
     * Creates the synchronous FalkorDB [GraphOperations] bean.
     *
     * ## Behavior / Contract
     * - Skips registration when another [GraphOperations] bean already exists.
     * - Uses the configured FalkorDB graph name from [FalkorDBGraphProperties.graphName].
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties): GraphOperations =
        FalkorDBGraphOperations(driver, props.graphName)

    /**
     * Creates the coroutine FalkorDB [GraphSuspendOperations] bean.
     *
     * ## Behavior / Contract
     * - Skips registration when another [GraphSuspendOperations] bean already exists.
     * - Registers by default and can be disabled with `bluetape4k.graph.falkordb.register-suspend=false`.
     * - Uses the configured FalkorDB graph name from [FalkorDBGraphProperties.graphName].
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.falkordb",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties): GraphSuspendOperations =
        FalkorDBGraphSuspendOperations(driver, props.graphName)

    /**
     * Creates the virtual-thread [GraphVirtualThreadOperations] adapter bean.
     *
     * ## Behavior / Contract
     * - Skips registration when another [GraphVirtualThreadOperations] bean already exists.
     * - Registers by default and can be disabled with `bluetape4k.graph.falkordb.register-virtual-thread=false`.
     * - Depends on the synchronous [GraphOperations] bean supplied to this adapter.
     * - Adapts the synchronous [GraphOperations] bean without changing the underlying FalkorDB driver ownership.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.falkordb",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    /**
     * Health indicator configuration for the FalkorDB backend.
     *
     * ## Behavior / Contract
     * - Loads only when Spring Boot Actuator's `HealthIndicator` type is present.
     * - Keeps Actuator-only bean signatures isolated from applications that do not use Actuator.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])
    class HealthConfig {

        companion object : KLogging()

        /**
         * Creates the FalkorDB health indicator bean.
         *
         * ## Behavior / Contract
         * - Skips registration when a bean named `falkordbHealthIndicator` already exists.
         * - Runs `RETURN 1` against the `__health__` graph to verify driver connectivity.
         * - Reports `UP` with the `backend=falkordb` detail on success and `DOWN` with the thrown exception on failure.
         */
        @Bean
        @ConditionalOnMissingBean(name = ["falkordbHealthIndicator"])
        fun falkordbHealthIndicator(driver: com.falkordb.Driver): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                try {
                    driver.graph("__health__").use { it.query("RETURN 1") }
                    org.springframework.boot.health.contributor.Health.up().withDetail("backend", "falkordb").build()
                } catch (e: Exception) {
                    org.springframework.boot.health.contributor.Health.down(e).build()
                }
            }
    }
}

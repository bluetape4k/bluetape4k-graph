package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot.properties.MemgraphGraphProperties
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Auto-configuration for the Memgraph backend.
 *
 * It is active when `bluetape4k.graph.backend=memgraph`. Memgraph speaks the
 * Neo4j Bolt protocol, so this configuration uses the Neo4j Java driver.
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
 *     "--bluetape4k.graph.backend=memgraph",
 *     "--bluetape4k.graph.memgraph.uri=bolt://localhost:7687",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(
    name = [
        "org.neo4j.driver.Driver",
        "io.bluetape4k.graph.memgraph.MemgraphGraphOperations",
    ]
)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "memgraph")
@EnableConfigurationProperties(MemgraphGraphProperties::class)
class GraphMemgraphAutoConfiguration {

    companion object : KLogging()

    /**
     * Memgraph driver bean.
     *
     * Memgraph uses the Neo4j Bolt-compatible Java driver. Only a bean explicitly named
     * `memgraphDriver` is reused, because a generic [Driver] bean may belong to a Neo4j backend.
     */
    @Bean(name = ["memgraphDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(name = ["memgraphDriver"])
    fun memgraphDriver(props: MemgraphGraphProperties): Driver {
        val auth = if (props.username.isBlank() || props.password.isBlank()) AuthTokens.none()
                   else AuthTokens.basic(props.username, props.password)
        val safeUri = props.uri.replace(Regex("//[^@]+@"), "//***@")
        log.info { "Creating Memgraph Driver: uri=$safeUri" }
        val config = Config.builder()
            .withConnectionTimeout(props.connectionTimeoutMillis, TimeUnit.MILLISECONDS)
            .withMaxConnectionLifetime(props.maxConnectionLifetimeMillis, TimeUnit.MILLISECONDS)
            .build()
        return GraphDatabase.driver(props.uri, auth, config)
    }

    /**
     * Memgraph 기반 `GraphOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(
        @Qualifier("memgraphDriver") driver: Driver,
        props: MemgraphGraphProperties,
    ): GraphOperations =
        MemgraphGraphOperations(driver, props.database)

    /**
     * Memgraph 기반 `GraphSuspendOperations` 빈 (코루틴).
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.memgraph",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(
        @Qualifier("memgraphDriver") driver: Driver,
        props: MemgraphGraphProperties,
    ): GraphSuspendOperations =
        MemgraphGraphSuspendOperations(driver, props.database)

    /**
     * Virtual Thread 기반 `GraphVirtualThreadOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.memgraph",
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
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])
    class HealthConfig {

        companion object : KLogging()

        @Bean
        @ConditionalOnMissingBean
        fun memgraphHealthIndicator(
            @Qualifier("memgraphDriver") driver: Driver,
        ): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                try {
                    driver.verifyConnectivity()
                    org.springframework.boot.health.contributor.Health.up().withDetail("backend", "memgraph").build()
                } catch (e: Exception) {
                    org.springframework.boot.health.contributor.Health.down(e).build()
                }
            }
    }
}

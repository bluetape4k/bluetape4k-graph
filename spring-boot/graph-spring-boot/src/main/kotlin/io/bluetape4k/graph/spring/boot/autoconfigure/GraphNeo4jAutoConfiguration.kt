package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot.properties.Neo4jGraphProperties
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Config
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Auto-configuration for the Neo4j backend.
 *
 * It is active when `bluetape4k.graph.backend=neo4j`. If the application
 * already provides a Neo4j [Driver], that driver is reused.
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
 *     "--bluetape4k.graph.backend=neo4j",
 *     "--bluetape4k.graph.neo4j.uri=bolt://localhost:7687",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(Driver::class, Neo4jGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "neo4j")
@EnableConfigurationProperties(Neo4jGraphProperties::class)
class GraphNeo4jAutoConfiguration {

    companion object : KLogging()

    /**
     * Neo4j Driver 빈. 이미 등록된 Driver 빈이 있으면 재사용한다.
     */
    @Bean(name = ["neo4jDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(Driver::class)
    fun neo4jDriver(props: Neo4jGraphProperties): Driver {
        val auth = if (props.username.isBlank() || props.password.isBlank()) AuthTokens.none()
                   else AuthTokens.basic(props.username, props.password)
        val safeUri = props.uri.replace(Regex("//[^@]+@"), "//***@")
        log.info { "Creating Neo4j Driver: uri=$safeUri, database=${props.database}" }
        val config = Config.builder()
            .withConnectionTimeout(props.connectionTimeoutMillis, TimeUnit.MILLISECONDS)
            .withMaxConnectionLifetime(props.maxConnectionLifetimeMillis, TimeUnit.MILLISECONDS)
            .build()
        return GraphDatabase.driver(props.uri, auth, config)
    }

    /**
     * Neo4j 기반 `GraphOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: Driver, props: Neo4jGraphProperties): GraphOperations =
        Neo4jGraphOperations(driver, props.database)

    /**
     * Neo4j 기반 `GraphSuspendOperations` 빈 (코루틴).
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.neo4j",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: Driver, props: Neo4jGraphProperties): GraphSuspendOperations =
        Neo4jGraphSuspendOperations(driver, props.database)

    /**
     * Virtual Thread 기반 `GraphVirtualThreadOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.neo4j",
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
        fun neo4jHealthIndicator(driver: Driver): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                try {
                    driver.verifyConnectivity()
                    org.springframework.boot.health.contributor.Health.up().withDetail("backend", "neo4j").build()
                } catch (e: Exception) {
                    org.springframework.boot.health.contributor.Health.down(e).build()
                }
            }
    }
}

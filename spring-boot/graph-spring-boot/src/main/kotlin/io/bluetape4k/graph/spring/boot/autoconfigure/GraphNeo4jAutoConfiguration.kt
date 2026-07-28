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
 * Neo4j backend auto-configuration.
 *
 * `bluetape4k.graph.backend=neo4j`일 때 활성화된다. Application이 이미 Neo4j [Driver]를 제공하면 그 driver를 재사용한다.
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
 *     "--bluetape4k.graph.backend=neo4j",
 *     "--bluetape4k.graph.neo4j.uri=bolt://localhost:7687",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(
    name = [
        "org.neo4j.driver.Driver",
        "io.bluetape4k.graph.neo4j.Neo4jGraphOperations",
    ]
)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "neo4j")
@EnableConfigurationProperties(Neo4jGraphProperties::class)
class GraphNeo4jAutoConfiguration {

    companion object : KLogging()

    /**
     * Application이 제공한 Neo4j [Driver]가 없을 때 새 [Driver]를 생성한다.
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
     * 설정된 database에 대해 Neo4j 기반 [GraphOperations]를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: Driver, props: Neo4jGraphProperties): GraphOperations =
        Neo4jGraphOperations(driver, props.database)

    /**
     * Suspend 지원이 활성화되면 coroutine 친화적 Neo4j graph operations를 등록한다.
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
     * 동기 Neo4j operations를 기반으로 virtual-thread graph operations를 등록한다.
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
     * Actuator health indicator를 분리해 Actuator를 사용하지 않는 application에서 `NoClassDefFoundError`를 피한다.
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
